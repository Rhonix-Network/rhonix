package io.rhonix.state

trait StateManager[F[_]] {
  // Checks if state is empty
  def isEmpty: F[Boolean]
}
