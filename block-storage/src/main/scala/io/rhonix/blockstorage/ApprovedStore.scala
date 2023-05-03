package io.rhonix.blockstorage

import cats.effect.Sync
import cats.syntax.all._
import io.rhonix.blockstorage.dag.codecs.codecFringe
import io.rhonix.casper.protocol.{FinalizedFringe, FinalizedFringeProto}
import io.rhonix.shared.syntax._
import io.rhonix.store.{KeyValueStoreManager, KeyValueTypedStore}
import scodec.codecs._

object approvedStore {
  type ApprovedStore[F[_]] = KeyValueTypedStore[F, Byte, FinalizedFringe]

  def ApprovedStore[F[_]](implicit instance: ApprovedStore[F]): instance.type = instance

  def create[F[_]: Sync](
      kvm: KeyValueStoreManager[F]
  ): F[KeyValueTypedStore[F, Byte, FinalizedFringe]] =
    kvm
      .store("finalized-store")
      .map(_.toTypedStore[Byte, FinalizedFringe](byte, codecFringe))

  def bytesToFringe(bytes: Array[Byte]): FinalizedFringe =
    FinalizedFringe.from(FinalizedFringeProto.parseFrom(bytes))

  def fringeToBytes(finalizedFringe: FinalizedFringe): Array[Byte] =
    finalizedFringe.toProto.toByteArray
}
