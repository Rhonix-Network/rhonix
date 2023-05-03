package io.rhonix.node.revvaultexport.reporting

import cats.Parallel
import cats.effect.{Concurrent, ContextShift, Sync}
import cats.syntax.all._
import com.google.protobuf.ByteString
import io.rhonix.blockstorage.dag.DagRepresentation
import io.rhonix.blockstorage.BlockStore
import io.rhonix.blockstorage.BlockStore.BlockStore
import io.rhonix.casper.dag.BlockDagKeyValueStorage
import io.rhonix.casper.genesis.contracts.StandardDeploys
import io.rhonix.casper.protocol.BlockMessage
import io.rhonix.casper.storage.RNodeKeyValueStoreManager
import io.rhonix.casper.syntax._
import io.rhonix.casper.util.{BondsParser, VaultParser}
import io.rhonix.crypto.PrivateKey
import io.rhonix.crypto.signatures.Secp256k1
import io.rhonix.metrics.{Metrics, NoopSpan, Span}
import io.rhonix.models.{BindPattern, ListParWithRandom, Par, TaggedContinuation}
import io.rhonix.node.revvaultexport.RhoTrieTraverser
import io.rhonix.node.web.{
  CloseBlock,
  PreCharge,
  Refund,
  SlashingDeploy,
  Transaction,
  TransactionInfo,
  UserDeploy
}
import io.rhonix.rholang.interpreter.RhoRuntime
import io.rhonix.rholang.interpreter.util.RevAddress
import io.rhonix.rspace.syntax._
import io.rhonix.rspace.{Match, RSpace}
import io.rhonix.models.syntax._
import io.rhonix.shared.{Base16, Log}
import io.rhonix.shared.syntax._

import java.nio.file.{Files, Path}
import scala.concurrent.ExecutionContext

object TransactionBalances {
  final case class TransactionBlockInfo(
      transaction: TransactionInfo,
      blockNumber: Long,
      isFinalized: Boolean
  ) {
    val isSucceed: Boolean = transaction.transaction.failReason.isEmpty
  }

  val initialPosStakingVault: RevAccount = RevAccount(
    RevAddress
      .fromPublicKey(
        Secp256k1.toPublic(PrivateKey(Base16.unsafeDecode(StandardDeploys.poSGeneratorPk)))
      )
      .get,
    0,
    PosStakingVault
  ) // 1111gW5kkGxHg7xDg6dRkZx2f7qxTizJzaCH9VEM1oJKWRvSX9Sk5
  val RhonixLabsVaultAddr = "11112q61nMYJKnJhQmqz7xKBNupyosG4Cy9rVupBPmpwcyT6s2SAoF"

  final case class DeployNotFound(transaction: TransactionInfo) extends Exception

  sealed trait AccountType
  object NormalVault                extends AccountType
  object PerValidatorVault          extends AccountType
  object PosStakingVault            extends AccountType
  object RhonixLabsPosMultiSigVault extends AccountType

  final case class RevAccount(address: RevAddress, amount: Long, accountType: AccountType) {
    def receiveRev(receiveAmount: Long): RevAccount = this.copy(amount = amount + receiveAmount)
    def sendRev(sendAmount: Long): RevAccount       = this.copy(amount = amount - sendAmount)

    def keccakHashedAddress: String =
      Base16.encode(RhoTrieTraverser.keccakParString(address.toBase58).drop(2))
    def typeString: String = accountType match {
      case NormalVault                => "NormalVault"
      case PerValidatorVault          => "PerValidatorVault"
      case PosStakingVault            => "PosStakingVault"
      case RhonixLabsPosMultiSigVault => "RhonixLabsPosMultiSigVault"
    }
  }

  type RevAddr = String

  /**
    * @param vaultMaps contains all the revVault account including posVaultAddress
    * `posVaultAddress`, `rhonixLabsPosMultiSigVault`, `perValidatorVaults` are just a marker for special addresses.
    */
  final case class GlobalVaultsInfo(
      vaultMaps: Map[RevAddr, RevAccount],
      posVaultAddress: RevAddr,
      rhonixLabsPosMultiSigVault: RevAddr,
      perValidatorVaults: Seq[RevAddr]
  )

  def getPerValidatorVaults[F[_]: Sync: Span: Log](
      runtime: RhoRuntime[F],
      block: BlockMessage
  ): F[Seq[RevAddr]] = {
    val contract = """new return, rl(`rho:registry:lookup`),
                    |  poSCh
                    |in {
                    |  rl!(`rho:rhonix:pos`, *poSCh) |
                    |  for(@(_, Pos) <- poSCh) {
                    |    @Pos!("getActiveValidatorVaults", *return)
                    |  }
                    |}""".stripMargin
    for {
      perValidatorVaults <- runtime.playExploratoryDeploy(
                             contract,
                             block.postStateHash
                           )
      perValidatorVaultAddr = perValidatorVaults.head.exprs.head.getEListBody.ps
        .map(p => p.exprs.head.getETupleBody.ps(1).exprs.head.getGString)
    } yield perValidatorVaultAddr
  }

  def generateRevAccountFromWalletAndBond[F[_]: Sync: ContextShift: Log](
      walletPath: Path,
      bondsPath: Path
  ): F[Map[String, RevAccount]] =
    for {
      bondsMap <- BondsParser.parse(bondsPath)
      vaults   <- VaultParser.parse(walletPath)
      accountMap = vaults
        .map(v => (v.revAddress.toBase58, RevAccount(v.revAddress, v.initialBalance, NormalVault)))
        .toMap
      revAccountMap = bondsMap.foldLeft(accountMap) {
        case (vaultMap, (_, bondAmount)) =>
          val posVault =
            vaultMap.getOrElse(initialPosStakingVault.address.toBase58, initialPosStakingVault)
          val newPosVault = posVault.receiveRev(bondAmount)
          vaultMap.updated(initialPosStakingVault.address.toBase58, newPosVault)
      }
    } yield revAccountMap

  def updateGenesisFromTransfer(
      genesisVault: GlobalVaultsInfo,
      transfers: List[TransactionBlockInfo]
  ): GlobalVaultsInfo = {
    val resultMap = transfers.foldLeft(genesisVault.vaultMaps) {
      case (m, transfer) =>
        if (transfer.isFinalized && transfer.isSucceed) {
          val fromAddr = transfer.transaction.transaction.fromAddr
          val toAddr   = transfer.transaction.transaction.toAddr
          val amount   = transfer.transaction.transaction.amount
          val fromVault = m.getOrElse(
            fromAddr,
            RevAccount(
              address = RevAddress.parse(fromAddr).right.get,
              amount = 0L,
              accountType = NormalVault
            )
          )
          val newVaultMap = m.updated(fromAddr, fromVault.sendRev(amount))
          val toVault = newVaultMap.getOrElse(
            toAddr,
            RevAccount(
              address = RevAddress.parse(toAddr).right.get,
              amount = 0L,
              accountType = NormalVault
            )
          )
          newVaultMap.updated(toAddr, toVault.receiveRev(amount))
        } else m

    }
    genesisVault.copy(vaultMaps = resultMap)
  }

  def getGenesisVaultMap[F[_]: Sync: ContextShift: Span: Log](
      walletPath: Path,
      bondsPath: Path,
      runtime: RhoRuntime[F],
      block: BlockMessage
  ): F[GlobalVaultsInfo] =
    for {
      vaultMap <- generateRevAccountFromWalletAndBond(walletPath, bondsPath)
      rhonixLabsVault = vaultMap.getOrElse(
        RhonixLabsVaultAddr,
        RevAccount(RevAddress.parse(RhonixLabsVaultAddr).right.get, 0, RhonixLabsPosMultiSigVault)
      )
      perValidatorVaults <- getPerValidatorVaults(runtime, block).map(
                             addrs =>
                               addrs.map(
                                 addr =>
                                   vaultMap.getOrElse(
                                     addr,
                                     RevAccount(
                                       RevAddress.parse(addr).right.get,
                                       0,
                                       PerValidatorVault
                                     )
                                   )
                               )
                           )
      genesisAccountMap = (rhonixLabsVault +: perValidatorVaults).foldLeft(vaultMap) {
        case (accountMap, account) => accountMap.updated(account.address.toBase58, account)
      }
      globalVaults = GlobalVaultsInfo(
        genesisAccountMap,
        initialPosStakingVault.address.toBase58,
        rhonixLabsVault.address.toBase58,
        perValidatorVaults.map(_.address.toBase58)
      )
    } yield globalVaults

  def getBlockHashByHeight[F[_]: Sync](
      blockNumber: Long,
      dag: DagRepresentation,
      blockStore: BlockStore[F]
  ): F[BlockMessage] = {

    import io.rhonix.blockstorage.syntax._
    for {
      blocks    <- dag.topoSortUnsafe(blockNumber.toLong, Some(blockNumber.toLong))
      blockHash = blocks.flatten.head
      block     <- blockStore.get1(blockHash)
      blockMes  = block.get
    } yield blockMes
  }

  def main[F[_]: Concurrent: Parallel: ContextShift](
      dataDir: Path,
      walletPath: Path,
      bondPath: Path,
      targetBlockHash: String
  )(implicit scheduler: ExecutionContext): F[(GlobalVaultsInfo, List[TransactionBlockInfo])] = {
    implicit val metrics: Metrics.MetricsNOP[F] = new Metrics.MetricsNOP[F]()
    import io.rhonix.rholang.interpreter.storage._
    implicit val span: NoopSpan[F]                           = NoopSpan[F]()
    implicit val log: Log[F]                                 = Log.log
    implicit val m: Match[F, BindPattern, ListParWithRandom] = matchListPar[F]
    for {
      rnodeStoreManager <- RNodeKeyValueStoreManager[F](dataDir)
      blockStore        <- BlockStore(rnodeStoreManager)
      store             <- rnodeStoreManager.rSpaceStores
      spaces <- RSpace
                 .createWithReplay[F, Par, BindPattern, ListParWithRandom, TaggedContinuation](
                   store
                 )
      (rSpacePlay, rSpaceReplay) = spaces
      runtimes <- RhoRuntime
                   .createRuntimes[F](
                     rSpacePlay,
                     rSpaceReplay,
                     initRegistry = true,
                     Seq.empty,
                     Par()
                   )
      (rhoRuntime, _)    = runtimes
      targetBlockOpt     <- blockStore.get1(targetBlockHash.unsafeHexToByteString)
      targetBlock        = targetBlockOpt.get
      _                  <- log.info(s"Getting balance from $targetBlock")
      genesisVaultMap    <- getGenesisVaultMap(walletPath, bondPath, rhoRuntime, targetBlock)
      transactionStore   <- Transaction.store(rnodeStoreManager)
      allTransactionsMap <- transactionStore.toMap
      allTransactions    = allTransactionsMap.values.flatMap(_.data)
      blockDagStorage    <- BlockDagKeyValueStorage.create[F](rnodeStoreManager)
      dagRepresantation  <- blockDagStorage.getRepresentation
      allWrappedTransactions <- {
        def findTransaction(transaction: TransactionInfo): F[ByteString] =
          transaction.transactionType match {
            case PreCharge(deployId) =>
              blockDagStorage
                .lookupByDeployId(deployId.unsafeHexToByteString)
                .flatMap(_.liftTo(DeployNotFound(transaction)))
            case Refund(deployId) =>
              blockDagStorage
                .lookupByDeployId(deployId.unsafeHexToByteString)
                .flatMap(_.liftTo(DeployNotFound(transaction)))
            case UserDeploy(deployId) =>
              blockDagStorage
                .lookupByDeployId(deployId.unsafeHexToByteString)
                .flatMap(_.liftTo(DeployNotFound(transaction)))
            case CloseBlock(blockHash) =>
              blockHash.unsafeHexToByteString.pure[F]
            case SlashingDeploy(blockHash) =>
              blockHash.unsafeHexToByteString.pure[F]
          }
        allTransactions.toList.traverse { t =>
          implicit val bds = blockDagStorage
          for {
            blockHash    <- findTransaction(t)
            blockMetaOpt <- bds.lookup(blockHash)
            blockMeta <- blockMetaOpt.liftTo(
                          new Exception(s"Block ${blockHash.toHexString} not found in dag")
                        )
            isFinalized         = dagRepresantation.isFinalized(blockHash)
            isBeforeTargetBlock = blockMeta.blockNum <= targetBlock.blockNumber
          } yield TransactionBlockInfo(t, blockMeta.blockNum, isFinalized && isBeforeTargetBlock)
        }
      }
      allSortedTransactions = allWrappedTransactions.sortBy(_.blockNumber)
      _ <- log.info(
            s"Transaction history from ${allSortedTransactions.head} to ${allSortedTransactions.tail}"
          )
      afterTransferMap = updateGenesisFromTransfer(genesisVaultMap, allSortedTransactions)
    } yield (afterTransferMap, allSortedTransactions)
  }
}
