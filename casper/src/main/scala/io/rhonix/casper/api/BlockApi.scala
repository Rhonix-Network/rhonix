package io.rhonix.casper.api

import cats.syntax.all._
import com.google.protobuf.ByteString
import io.rhonix.blockstorage.dag.BlockDagStorage.DeployId
import io.rhonix.casper.PrettyPrinter
import io.rhonix.casper.api.BlockApi.ApiErr
import io.rhonix.casper.protocol._
import io.rhonix.casper.protocol.deploy.v1.DeployExecStatus
import io.rhonix.crypto.signatures.Signed
import io.rhonix.models.Validator.Validator
import io.rhonix.models.{BlockMetadata, Par}

trait BlockApi[F[_]] {
  def status: F[Status]

  def deploy(d: Signed[DeployData]): F[ApiErr[String]]

  def deployStatus(deployId: DeployId): F[ApiErr[DeployExecStatus]]

  def createBlock(isAsync: Boolean): F[ApiErr[String]]

  def getProposeResult: F[ApiErr[String]]

  def getListeningNameDataResponse(
      depth: Int,
      listeningName: Par
  ): F[ApiErr[(Seq[DataWithBlockInfo], Int)]]

  def getListeningNameContinuationResponse(
      depth: Int,
      listeningNames: Seq[Par]
  ): F[ApiErr[(Seq[ContinuationsWithBlockInfo], Int)]]

  def getBlocksByHeights(
      startBlockNumber: Long,
      endBlockNumber: Long
  ): F[ApiErr[List[LightBlockInfo]]]

  def visualizeDag[R](
      depth: Int,
      startBlockNumber: Int,
      showJustificationLines: Boolean
  ): F[ApiErr[Vector[String]]]

  def machineVerifiableDag(depth: Int): F[ApiErr[String]]

  def getBlocks(depth: Int): F[ApiErr[List[LightBlockInfo]]]

  def findDeploy(id: DeployId): F[ApiErr[LightBlockInfo]]

  def getBlock(hash: String): F[ApiErr[BlockInfo]]

  def bondStatus(
      publicKey: ByteString,
      targetBlock: Option[BlockMessage] = none[BlockMessage]
  ): F[ApiErr[Boolean]]

  def exploratoryDeploy(
      term: String,
      blockHash: Option[String],
      usePreStateHash: Boolean
  ): F[ApiErr[(Seq[Par], LightBlockInfo)]]

  def getDataAtPar(
      par: Par,
      blockHash: String,
      usePreStateHash: Boolean
  ): F[ApiErr[(Seq[Par], LightBlockInfo)]]

  def lastFinalizedBlock: F[ApiErr[BlockInfo]]

  def isFinalized(hash: String): F[ApiErr[Boolean]]

  def getLatestMessage: F[ApiErr[BlockMetadata]]
}

object BlockApi {
  type Error     = String
  type ApiErr[A] = Either[Error, A]

  def getFullBlockInfo(block: BlockMessage): BlockInfo =
    constructBlockInfo(block)

  def getLightBlockInfo(block: BlockMessage): LightBlockInfo =
    constructLightBlockInfo(block)

  def bondToBondInfo(bond: (Validator, Long)): BondInfo =
    BondInfo(validator = PrettyPrinter.buildStringNoLimit(bond._1), stake = bond._2)

  private def constructBlockInfo(block: BlockMessage): BlockInfo = {
    val lightBlockInfo = constructLightBlockInfo(block)
    val deploys        = block.state.deploys.map(_.toDeployInfo)
    BlockInfo(blockInfo = lightBlockInfo, deploys = deploys)
  }

  private def constructLightBlockInfo(block: BlockMessage): LightBlockInfo =
    LightBlockInfo(
      shardId = block.shardId,
      blockHash = PrettyPrinter.buildStringNoLimit(block.blockHash),
      sender = PrettyPrinter.buildStringNoLimit(block.sender),
      seqNum = block.seqNum,
      sig = PrettyPrinter.buildStringNoLimit(block.sig),
      sigAlgorithm = block.sigAlgorithm,
      version = block.version,
      blockNumber = block.blockNumber,
      preStateHash = PrettyPrinter.buildStringNoLimit(block.preStateHash),
      postStateHash = PrettyPrinter.buildStringNoLimit(block.postStateHash),
      bonds = block.bonds.map(bondToBondInfo).toList,
      blockSize = block.toProto.serializedSize.toString,
      deployCount = block.state.deploys.length,
      justifications = block.justifications.map(PrettyPrinter.buildStringNoLimit),
      rejectedDeploys = block.rejectedDeploys.toSeq.map(PrettyPrinter.buildStringNoLimit)
    )
}
