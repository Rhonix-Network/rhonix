package io.rhonix.casper.engine

import cats.Parallel
import cats.effect.concurrent.Deferred
import cats.effect.{Concurrent, ContextShift, Timer}
import cats.syntax.all._
import io.rhonix.blockstorage.BlockStore
import io.rhonix.blockstorage.BlockStore.BlockStore
import io.rhonix.blockstorage.approvedStore.ApprovedStore
import io.rhonix.blockstorage.dag.BlockDagStorage
import io.rhonix.casper._
import io.rhonix.casper.blocks.BlockRetriever
import io.rhonix.casper.genesis.Genesis
import io.rhonix.casper.genesis.contracts.{ProofOfStake, Registry, Validator}
import io.rhonix.casper.protocol._
import io.rhonix.casper.rholang.RuntimeManager
import io.rhonix.casper.syntax._
import io.rhonix.casper.util.{BondsParser, VaultParser}
import io.rhonix.comm.PeerNode
import io.rhonix.comm.rp.Connect.{ConnectionsCell, RPConfAsk}
import io.rhonix.comm.transport.TransportLayer
import io.rhonix.metrics.{Metrics, Span}
import io.rhonix.models.BlockMetadata
import io.rhonix.rspace.state.RSpaceStateManager
import io.rhonix.shared._
import io.rhonix.shared.syntax._
import fs2.Stream
import fs2.concurrent.Queue

import scala.concurrent.duration.DurationInt

final case class PeerMessage(peer: PeerNode, message: CasperMessage)

object NodeLaunch {

  // format: off
  def apply[F[_]
    /* Execution */   : Concurrent: Parallel: ContextShift: Time: Timer
    /* Transport */   : TransportLayer: CommUtil: BlockRetriever
    /* State */       : RPConfAsk: ConnectionsCell
    /* Rholang */     : RuntimeManager
    /* Storage */     : BlockStore: ApprovedStore: BlockDagStorage: RSpaceStateManager
    /* Diagnostics */ : Log: Metrics: Span] // format: on
  (
      packets: Stream[F, PeerMessage],
      incomingBlocksQueue: Queue[F, BlockMessage],
      conf: CasperConf,
      trimState: Boolean,
      disableStateExporter: Boolean,
      validatorIdentityOpt: Option[ValidatorIdentity],
      standalone: Boolean
  ): F[Unit] = {

    def noValidatorIdentityError =
      new Exception("To create genesis block node must provide validator private key")

    def createStoreBroadcastGenesis: F[Unit] =
      for {
        _ <- Log[F]
              .warn(
                "Public key for PoS vault is not set, manual transfer from PoS vault will be disabled (config 'pos-vault-pub-key')"
              )
              .whenA(conf.genesisBlockData.posVaultPubKey.isEmpty)
        _ <- Log[F]
              .warn(
                "Public key for system - contract is not set (config 'system-contract-pub-key')"
              )
              .whenA(conf.genesisBlockData.systemContractPubKey.isEmpty)

        // Get creator public key
        validatorIdentity <- validatorIdentityOpt.liftTo(noValidatorIdentityError)
        genesisBlock      <- createGenesisBlockFromConfig(validatorIdentity, conf)
        genBlockStr       = PrettyPrinter.buildString(genesisBlock)
        _                 <- Log[F].info(s"Sending genesis block $genBlockStr to peers...")

        // Store genesis block
        _             <- BlockStore[F].put(genesisBlock)
        genesisFringe = FinalizedFringe(hashes = Seq(), genesisBlock.preStateHash)
        _             <- ApprovedStore[F].putApprovedBlock(genesisFringe)

        // Add genesis block to DAG
        // TODO: replay genesis block to confirm creation was correct, it's fatal error if replay fails
        _ <- BlockDagStorage[F].insertGenesis(genesisBlock)

        // Send fringe data to peers
        _ <- CommUtil[F].streamToPeers(genesisFringe.toProto)
      } yield ()

    def startSyncingMode: F[Unit] =
      for {
        validatorId <- ValidatorIdentity.fromPrivateKeyWithLogging[F](conf.validatorPrivateKey)
        finished    <- Deferred[F, Unit]
        engine      <- NodeSyncing[F](finished, validatorId, trimState)
        handleMessages = packets.parEvalMapUnorderedProcBounded { pm =>
          engine.handle(pm.peer, pm.message)
        }
        _ <- CommUtil[F].requestFinalizedFringe(trimState)
        _ <- (Stream.eval(finished.get) concurrently handleMessages).compile.drain
      } yield ()

    def startRunningMode: F[Unit] =
      for {
        engine <- NodeRunning[F](incomingBlocksQueue, validatorIdentityOpt, disableStateExporter)
        handleMessages = packets.parEvalMapUnorderedProcBounded { pm =>
          engine.handle(pm.peer, pm.message)
        }
        _ <- Log[F].info(s"Making a transition to Running state.")

        // Wait for first connection before sending requests or handling incoming messages
        // TODO: this is part of legacy logic, send FCT request to at least one node
        //  - this should be part of network layer to ensure messages are sent to right peers
        _ <- waitForFirstConnection
        _ <- CommUtil[F].sendForkChoiceTipRequest

        _ <- handleMessages.compile.drain
      } yield ()

    def waitForFirstConnection: F[Unit] =
      for {
        isEmpty <- ConnectionsCell[F].get.map(_.isEmpty)
        _       <- (Timer[F].sleep(250.millis) *> waitForFirstConnection).whenA(isEmpty)
      } yield ()

    for {
      dag <- BlockDagStorage[F].getRepresentation
      _ <- if (dag.dagSet.isEmpty && standalone) {
            // Create genesis block and send to peers
            Log[F].info("Starting as genesis master, creating genesis block...") *>
              createStoreBroadcastGenesis
          } else if (dag.dagSet.isEmpty) {
            // If state is empty, transition to syncing mode (LFS)
            Log[F].info("Starting from bootstrap node, syncing LFS...") *>
              startSyncingMode
          } else {
            Log[F].info("Reconnecting to existing network...")
          }

      // Transition to running mode
      _ <- startRunningMode
    } yield ()
  }

  def createGenesisBlockFromConfig[F[_]: Concurrent: ContextShift: RuntimeManager: Log](
      validator: ValidatorIdentity,
      conf: CasperConf
  ): F[BlockMessage] =
    createGenesisBlock[F](
      validator,
      conf.shardName,
      conf.genesisBlockData.genesisBlockNumber,
      conf.genesisBlockData.bondsFile,
      conf.autogenShardSize,
      conf.genesisBlockData.walletsFile,
      conf.genesisBlockData.bondMinimum,
      conf.genesisBlockData.bondMaximum,
      conf.genesisBlockData.epochLength,
      conf.genesisBlockData.quarantineLength,
      conf.genesisBlockData.numberOfActiveValidators,
      conf.genesisBlockData.posMultiSigPublicKeys,
      conf.genesisBlockData.posMultiSigQuorum,
      conf.genesisBlockData.posVaultPubKey,
      conf.genesisBlockData.systemContractPubKey
    )

  def createGenesisBlock[F[_]: Concurrent: ContextShift: RuntimeManager: Log](
      validator: ValidatorIdentity,
      shardId: String,
      blockNumber: Long,
      bondsPath: String,
      autogenShardSize: Int,
      vaultsPath: String,
      minimumBond: Long,
      maximumBond: Long,
      epochLength: Int,
      quarantineLength: Int,
      numberOfActiveValidators: Int,
      posMultiSigPublicKeys: List[String],
      posMultiSigQuorum: Int,
      posVaultPubKey: String,
      systemContractPubkey: String
  ): F[BlockMessage] =
    for {
      // Initial REV vaults
      vaults <- VaultParser.parse[F](vaultsPath)

      // Initial validators
      bonds      <- BondsParser.parse[F](bondsPath, autogenShardSize)
      validators = bonds.toSeq.map(Validator.tupled)

      // Run genesis deploys and create block
      genesisBlock <- Genesis.createGenesisBlock(
                       validator,
                       Genesis(
                         sender = validator.publicKey,
                         shardId = shardId,
                         proofOfStake = ProofOfStake(
                           minimumBond = minimumBond,
                           maximumBond = maximumBond,
                           epochLength = epochLength,
                           quarantineLength = quarantineLength,
                           numberOfActiveValidators = numberOfActiveValidators,
                           validators = validators,
                           posMultiSigPublicKeys = posMultiSigPublicKeys,
                           posMultiSigQuorum = posMultiSigQuorum,
                           posVaultPubKey = posVaultPubKey
                         ),
                         registry = Registry(systemContractPubkey),
                         vaults = vaults,
                         blockNumber = blockNumber
                       )
                     )
    } yield genesisBlock
}
