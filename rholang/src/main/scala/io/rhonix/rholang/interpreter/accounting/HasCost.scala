package io.rhonix.rholang.interpreter.accounting

trait HasCost[F[_]] {
  def cost: _cost[F]
}
