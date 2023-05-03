package io.rhonix.models

import com.google.protobuf.ByteString
import io.rhonix.casper.protocol.FringeDataProto
import io.rhonix.models.BlockHash.BlockHash
import io.rhonix.models.syntax._
import io.rhonix.rspace.hashing.Blake2b256Hash
import scodec.bits.ByteVector

final case class FringeData(
    fringeHash: Blake2b256Hash,
    fringe: Set[BlockHash],
    fringeDiff: Set[BlockHash],
    stateHash: Blake2b256Hash,
    // Rejected data in finalized fringe
    rejectedDeploys: Set[ByteString],
    rejectedBlocks: Set[BlockHash],
    rejectedSenders: Set[ByteString]
) {
  // FringeData is uniquely identified with the hash of fringe hashes
  // - overridden hashCode is to be more performant when used in Set or Map
  override def hashCode(): Int = fringeHash.hashCode()
}

object FringeData {

  /**
    * Creates a hash from sorted fringe (block hashes).
    *
    * Used like a primary key in fringe data store.
    */
  def fringeHash(fringe: Set[BlockHash]) = {
    val sortedFringe = fringe.toSeq.sorted.map(x => ByteVector(x.toByteArray))

    Blake2b256Hash.create(sortedFringe)
  }

  def from(b: FringeDataProto) = FringeData(
    b.fringeHash.toBlake2b256Hash,
    b.fringe.toSet,
    b.fringeDiff.toSet,
    b.stateHash.toBlake2b256Hash,
    b.rejectedDeploys.toSet,
    b.rejectedBlocks.toSet,
    b.rejectedSenders.toSet
  )

  def toProto(b: FringeData) = FringeDataProto(
    b.fringeHash.toByteString,
    b.fringe.toList,
    b.fringeDiff.toList,
    b.stateHash.toByteString,
    b.rejectedDeploys.toList,
    b.rejectedBlocks.toList,
    b.rejectedSenders.toList
  )

  def fromBytes(bytes: Array[Byte]): FringeData =
    from(FringeDataProto.parseFrom(bytes))

  def toBytes(b: FringeData) = FringeData.toProto(b).toByteArray
}
