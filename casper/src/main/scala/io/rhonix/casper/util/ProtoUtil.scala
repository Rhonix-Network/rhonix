package io.rhonix.casper.util

import cats.effect.Sync
import cats.syntax.all._
import com.google.protobuf.ByteString
import io.rhonix.blockstorage.dag.BlockDagStorage
import io.rhonix.blockstorage.syntax._
import io.rhonix.casper.protocol._
import io.rhonix.crypto.PublicKey
import io.rhonix.crypto.hash.Blake2b256
import io.rhonix.crypto.signatures.Secp256k1
import io.rhonix.models.BlockHash.BlockHash
import io.rhonix.models.Validator.Validator
import io.rhonix.models._
import io.rhonix.models.syntax._

object ProtoUtil {

  def getParentsMetadata[F[_]: Sync: BlockDagStorage](b: BlockMetadata): F[List[BlockMetadata]] =
    b.justifications.toList
      .traverse(BlockDagStorage[F].lookupUnsafe(_))
      .map(_.filter(!_.validationFailed))

  def getParentMetadatasAboveBlockNumber[F[_]: Sync: BlockDagStorage](
      b: BlockMetadata,
      blockNumber: Long
  ): F[List[BlockMetadata]] =
    getParentsMetadata(b).map(parents => parents.filter(p => p.blockNum >= blockNumber))

  def maxBlockNumberMetadata(blocks: Seq[BlockMetadata]): Long = blocks.foldLeft(-1L) {
    case (acc, b) => math.max(acc, b.blockNum)
  }

  def unsignedBlockProto(
      version: Int,
      shardId: String,
      blockNumber: Long,
      sender: PublicKey,
      seqNum: Long,
      preStateHash: ByteString,
      postStateHash: ByteString,
      justifications: List[BlockHash],
      bonds: Map[Validator, Long],
      rejectedDeploys: Set[ByteString],
      state: RholangState
  ): BlockMessage = {
    val block = BlockMessage(
      version,
      shardId,
      blockHash = ByteString.EMPTY,
      blockNumber = blockNumber,
      sender = sender.bytes.toByteString,
      seqNum = seqNum,
      preStateHash = preStateHash,
      postStateHash = postStateHash,
      justifications,
      bonds,
      rejectedDeploys,
      rejectedBlocks = Set(),
      rejectedSenders = Set(),
      state,
      // Signature algorithm is now part of the block hash
      //  so it should be set immediately.
      // [TG] I couldn't find a reason why is part of block hash.
      sigAlgorithm = Secp256k1.name,
      sig = ByteString.EMPTY
    )

    val hash = hashBlock(block)

    block.copy(blockHash = hash)
  }

  /**
    * Create hash of a BlockMessage, all fields must be included except signature
    */
  def hashBlock(blockMessage: BlockMessage): BlockHash = {
    val emptyBytes = ByteString.EMPTY
    val blockClearSigData = blockMessage.copy(
      blockHash = emptyBytes,
      sig = emptyBytes
    )
    Blake2b256.hash(blockClearSigData.toProto.toByteArray).toByteString
  }
}
