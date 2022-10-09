package io.rhonix.casper.blocks.proposer

import cats.data.OptionT
import cats.effect.concurrent.Deferred
import cats.effect.{Concurrent, Timer}
import cats.syntax.all._
import com.google.protobuf.ByteString
import io.rhonix.blockstorage.BlockStore
import io.rhonix.blockstorage.BlockStore.BlockStore
import io.rhonix.blockstorage.dag.BlockDagStorage
import io.rhonix.casper._
import io.rhonix.casper.protocol.{BlockMessage, CommUtil}
import io.rhonix.casper.rholang.RuntimeManager
import io.rhonix.casper.syntax._
import io.rhonix.casper.util.ConstructDeploy
import io.rhonix.crypto.PrivateKey
import io.rhonix.metrics.Metrics.Source
import io.rhonix.metrics.implicits._
import io.rhonix.metrics.{Metrics, Span}
import io.rhonix.models.BlockHash.BlockHash
import io.rhonix.models.Validator.Validator
import io.rhonix.models.syntax._
import io.rhonix.sdk.consensus.Stake
import io.rhonix.sdk.error.FatalError
import io.rhonix.shared.syntax._
import io.rhonix.shared.{Log, Time}

sealed abstract class ProposerResult
object ProposerEmpty                                                         extends ProposerResult
final case class ProposerSuccess(status: ProposeStatus, block: BlockMessage) extends ProposerResult
final case class ProposerFailure(status: ProposeStatus, seqNumber: Long)     extends ProposerResult
final case class ProposerStarted(seqNumber: Long)                            extends ProposerResult

object ProposerResult {
  def empty: ProposerResult = ProposerEmpty
  def success(status: ProposeStatus, block: BlockMessage): ProposerResult =
    ProposerSuccess(status, block)
  def failure(status: ProposeStatus, seqNumber: Long): ProposerResult =
    ProposerFailure(status, seqNumber)
  def started(seqNumber: Long): ProposerResult = ProposerStarted(seqNumber)
}

class Proposer[F[_]: Concurrent: Log: Span](
    getLatestSeqNumber: Validator => F[Long],
    // propose constraint checkers
    checkActiveValidator: ValidatorIdentity => F[Boolean],
    createBlock: ValidatorIdentity => F[BlockCreatorResult],
    validateBlock: BlockMessage => F[ValidBlockProcessing],
    proposeEffect: BlockMessage => F[Unit],
    validator: ValidatorIdentity
) {

  implicit val RuntimeMetricsSource: Source = Metrics.Source(CasperMetricsSource, "proposer")
  // This is the whole logic of propose
  private def doPropose: F[(ProposeResult, Option[BlockMessage])] =
    Span[F].traceI("do-propose") {
      // check if node is allowed to propose a block
      checkActiveValidator(validator).ifM(
        for {
          b <- createBlock(validator)
          r <- b match {
                case NoNewDeploys =>
                  (ProposeResult.failure(NoNewDeploys), none[BlockMessage]).pure[F]
                case Created(b) =>
                  validateBlock(b).flatMap {
                    case Right(v) =>
                      proposeEffect(b) >>
                        (ProposeResult.success(v), b.some).pure[F]
                    case Left(v) =>
                      Concurrent[F].raiseError[(ProposeResult, Option[BlockMessage])](
                        new Exception(
                          s"Validation of self created block failed with reason: $v, cancelling propose."
                        )
                      )
                  }
              }
        } yield r,
        (ProposeResult.failure(NotBonded), none[BlockMessage]).pure[F]
      )
    }

  def propose(
      isAsync: Boolean,
      proposeIdDef: Deferred[F, ProposerResult]
  ): F[(ProposeResult, Option[BlockMessage])] = {
    val valBytes = ByteString.copyFrom(validator.publicKey.bytes)
    for {
      validatorSeqNum <- getLatestSeqNumber(valBytes)
      nextSeq         = validatorSeqNum + 1L
      result <- if (isAsync) for {
                 _ <- proposeIdDef.complete(ProposerResult.started(nextSeq))

                 // propose
                 r <- doPropose
               } yield r
               else
                 for {
                   // propose
                   r <- doPropose

                   (result, blockHashOpt) = r
                   proposerResult = blockHashOpt.fold {
                     ProposerResult.failure(result.proposeStatus, nextSeq)
                   } { block =>
                     ProposerResult.success(result.proposeStatus, block)
                   }
                   _ <- proposeIdDef.complete(proposerResult)
                 } yield r

    } yield result
  }
}

object Proposer {
  // format: off
  def apply[F[_]
    /* Execution */   : Concurrent: Timer: Time
    /* Storage */     : BlockStore: BlockDagStorage
    /* Rholang */     : RuntimeManager
    /* Comm */        : CommUtil
    /* Diagnostics */ : Log: Span: Metrics
  ] // format: on
  (
      validatorIdentity: ValidatorIdentity,
      shardId: String,
      minPhloPrice: Long,
      epochLength: Int,
      dummyDeployOpt: Option[(PrivateKey, String)] = None
  ): Proposer[F] = {
    // TODO: refactor proposer to get this from parent pre state
    def getLatestSeqNumber(sender: Validator): F[Long] =
      for {
        dag        <- BlockDagStorage[F].getRepresentation
        latestMsgs = dag.dagMessageState.latestMsgs
        maxSeqNum  = latestMsgs.find(_.sender == sender).map(_.senderSeq)
      } yield maxSeqNum.getOrElse(-1)

    def createBlock(validatorIdentity: ValidatorIdentity): F[BlockCreatorResult] =
      for {
        // merge pre state
        preState <- MultiParentCasper.getPreStateForNewBlock
        // misc
        preStateHash      = preState.preStateHash
        creatorsPk        = validatorIdentity.publicKey
        creatorsId        = ByteString.copyFrom(creatorsPk.bytes)
        creatorsLatestOpt = preState.justifications.find(_.sender == creatorsId)
        nextSeqNum        = creatorsLatestOpt.map(_.seqNum + 1).getOrElse(0L)
        nextBlockNum      = preState.justifications.map(_.blockNum).max + 1
        parentHashes      = preState.justifications.map(_.blockHash)
        finalBonds        = preState.fringeBondsMap
        offenders         = preState.justifications.filter(_.validationFailed).map(_.sender)
        // slashing
        preStateBonds <- RuntimeManager[F].computeBonds(preStateHash.toByteString)
        toSlash       = offenders intersect preStateBonds.filter { case (_, b) => b > 0 }.keySet
        _             <- Log[F].info(s"Slashing senders: [${toSlash.map(_.show).mkString("; ")}]")
        // epoch
        changeEpoch = epochLength % nextBlockNum == 0
        // attestation
        // no need to attest if nothing meaningful to finalize.
        dag         <- BlockDagStorage[F].getRepresentation
        seen        = dag.dagMessageState.msgMap(_: BlockHash).seen
        conflictSet = parentHashes.flatMap(seen) -- preState.fringe.flatMap(seen)
        hasDeploys  = (b: BlockMessage) => b.state.systemDeploys.nonEmpty || b.state.deploys.nonEmpty
        nothingToFinalize = conflictSet.toList
          .traverse(BlockStore[F].getUnsafe)
          .map(!_.exists(hasDeploys))
        waitingForSupermajorityToAttest = {
          val newlySeen = creatorsLatestOpt
            .map(_.justifications.flatMap(seen) -- parentHashes.flatMap(seen))
            .getOrElse(Set())
          newlySeen.toList.traverse(BlockStore[F].getUnsafe).map { newBlocks =>
            val newStateTransition = newBlocks.exists(hasDeploys)
            val attestationStake =
              preStateBonds.filterKeys(newBlocks.map(_.sender).toSet).values.toList.sum
            val preStateBondsStake = preStateBonds.values.toList.sum

            !(newStateTransition || Stake.isSuperMajority(attestationStake, preStateBondsStake))
          }
        }
        suppressAttestation <- nothingToFinalize ||^ waitingForSupermajorityToAttest
        // user deploys
        pooled <- BlockDagStorage[F].pooledDeploys
        pooledOk <- pooled.toList
                     .filterA {
                       case (id, d) =>
                         val future       = d.data.validAfterBlockNumber > nextBlockNum
                         val expired      = d.data.validAfterBlockNumber < nextBlockNum - MultiParentCasper.deployLifespan
                         val replayAttack = BlockDagStorage[F].lookupByDeployId(id).map(_.nonEmpty)
                         (future.pure ||^ expired.pure ||^ replayAttack).not
                     }
                     .map(_.map(_._1))
        deploys <- {
          val dummy = dummyDeployOpt
            .traverse {
              case (privateKey, term) =>
                val deployData = ConstructDeploy.sourceDeployNow(
                  source = term,
                  sec = privateKey,
                  vabn = nextBlockNum - 1,
                  shardId = shardId
                )
                BlockDagStorage[F].addDeploy(deployData).as(List(deployData.sig))
            }
          OptionT
            .whenF(pooledOk.nonEmpty)(pooledOk.pure)
            .orElseF(dummy)
            .value
            .map(_.getOrElse(List()))
        }
        // create block
        _ <- Log[F].info(s"Creating block #${nextBlockNum} (seqNum ${nextSeqNum})")
        result <- BlockCreator(validatorIdentity, shardId).create(
                   preState,
                   deploys,
                   toSlash,
                   changeEpoch,
                   suppressAttestation
                 )
      } yield result

    def validateBlock(block: BlockMessage) =
      MultiParentCasper.validate(block, shardId, minPhloPrice).flatMap { result =>
        result
          .map { blockMeta =>
            BlockDagStorage[F].insert(blockMeta, block).as(BlockStatus.valid.asRight[InvalidBlock])
          }
          .leftMap {
            case (_, err) =>
              FatalError(s"Failed to replay own block: $err").raiseError[F, ValidBlockProcessing]
          }
          .merge
      }

    def checkValidatorIsActive(validator: ValidatorIdentity): F[Boolean] =
      for {
        dag          <- BlockDagStorage[F].getRepresentation
        latestFringe = dag.dagMessageState.latestFringe
        // TODO: take bonds map from merged state of fringe
        //  - it should also include consensus bonds map
        bondsMap <- if (latestFringe.nonEmpty) latestFringe.head.bondsMap.pure[F]
                   else BlockDagStorage[F].lookupUnsafe(dag.heightMap.head._2.head).map(_.bondsMap)
        sender = ByteString.copyFrom(validator.publicKey.bytes)
      } yield bondsMap.contains(sender)

    val proposeEffect = (b: BlockMessage) =>
      // store block
      BlockStore[F].put(b) >>
        // save changes to Casper
        // TODO: temp this is done after validation
//        MultiParentCasper.handleValidBlock(b) >>
        // broadcast hash to peers
        CommUtil[F].sendBlockHash(b.blockHash, b.sender)

    new Proposer(
      getLatestSeqNumber,
      checkValidatorIsActive,
      createBlock,
      validateBlock,
      proposeEffect,
      validatorIdentity
    )
  }
}
