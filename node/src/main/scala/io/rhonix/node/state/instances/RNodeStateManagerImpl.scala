package io.rhonix.node.state.instances

import cats.effect.Sync
import io.rhonix.casper.state.{BlockStateManager, RNodeStateManager}
import io.rhonix.catscontrib.Catscontrib.ToBooleanF
import io.rhonix.rspace.state.RSpaceStateManager

object RNodeStateManagerImpl {
  def apply[F[_]: Sync](
      rSpaceStateManager: RSpaceStateManager[F],
      blockStateManager: BlockStateManager[F]
  ): RNodeStateManager[F] =
    RNodeStateManagerImpl[F](rSpaceStateManager, blockStateManager)

  private final case class RNodeStateManagerImpl[F[_]: Sync](
      rSpaceStateManager: RSpaceStateManager[F],
      blockStateManager: BlockStateManager[F]
  ) extends RNodeStateManager[F] {
    override def isEmpty: F[Boolean] = rSpaceStateManager.isEmpty &&^ blockStateManager.isEmpty
  }
}
