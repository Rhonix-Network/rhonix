package io.rhonix.blockstorage

import cats.effect.Sync
import io.rhonix.blockstorage.approvedStore.ApprovedStore
import io.rhonix.casper.protocol.FinalizedFringe
import io.rhonix.shared.syntax._

trait ApprovedStoreSyntax {
  implicit final def syntaxApprovedStore[F[_]: Sync](
      approvedStore: ApprovedStore[F]
  ): ApprovedStoreOps[F] =
    new ApprovedStoreOps[F](approvedStore)
}

final class ApprovedStoreOps[F[_]: Sync](
    // ApprovedStore extensions / syntax
    private val approvedStore: ApprovedStore[F]
) {
  val approvedBlockKey: Byte = 42.toByte

  def getApprovedBlock: F[Option[FinalizedFringe]] = approvedStore.get1(approvedBlockKey)

  def putApprovedBlock(block: FinalizedFringe): F[Unit] = approvedStore.put(approvedBlockKey, block)
}
