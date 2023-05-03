package io.rhonix.rspace.history

import cats.effect.Sync
import io.rhonix.rspace.hashing.Blake2b256Hash.codecBlake2b256Hash
import io.rhonix.rspace.hashing.Blake2b256Hash
import io.rhonix.shared.Serialize._
import io.rhonix.shared.syntax._
import io.rhonix.store.{KeyValueStore, KeyValueTypedStore}
import scodec.Codec
import scodec.bits.ByteVector
import scodec.codecs.{discriminated, uint2}

object ColdStoreInstances {
  type ColdKeyValueStore[F[_]] = KeyValueTypedStore[F, Blake2b256Hash, PersistedData]

  def coldStore[F[_]: Sync](store: KeyValueStore[F]): ColdKeyValueStore[F] =
    store.toTypedStore(codecBlake2b256Hash, codecPersistedData)

  val codecPersistedData: Codec[PersistedData] =
    discriminated[PersistedData]
      .by(uint2)
      .subcaseP(0) {
        case n: JoinsLeaf => n
      }(codecByteVector.as[JoinsLeaf])
      .subcaseP(1) {
        case s: DataLeaf => s
      }(codecByteVector.as[DataLeaf])
      .subcaseP(2) {
        case c: ContinuationsLeaf => c
      }(codecByteVector.as[ContinuationsLeaf])
}

sealed trait PersistedData {
  def bytes: ByteVector
}

final case class JoinsLeaf(bytes: ByteVector)         extends PersistedData
final case class DataLeaf(bytes: ByteVector)          extends PersistedData
final case class ContinuationsLeaf(bytes: ByteVector) extends PersistedData
