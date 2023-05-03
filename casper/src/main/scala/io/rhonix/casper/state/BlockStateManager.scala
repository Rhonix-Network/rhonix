package io.rhonix.casper.state

import io.rhonix.state.StateManager

trait BlockStateManager[F[_]] extends StateManager[F]

final case class BlockStateStatus()

object BlockStateManager {
  def apply[F[_]](implicit instance: BlockStateManager[F]): BlockStateManager[F] = instance
}
