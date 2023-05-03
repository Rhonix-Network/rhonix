package io.rhonix.casper.state

import io.rhonix.state.StateManager

trait RNodeStateManager[F[_]] extends StateManager[F]

object RNodeStateManager {
  def apply[F[_]](implicit instance: RNodeStateManager[F]): RNodeStateManager[F] = instance
}
