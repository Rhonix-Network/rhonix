package io.rhonix.casper.helper
import cats.Parallel
import cats.effect.{Concurrent, ContextShift, Resource}
import io.rhonix.metrics.{Metrics, Span}
import io.rhonix.rholang.Resources.mkRuntimes
import io.rhonix.rholang.interpreter.RhoRuntime.RhoHistoryRepository
import io.rhonix.rholang.interpreter.{ReplayRhoRuntime, RhoRuntime}
import io.rhonix.shared.Log
import monix.execution.Scheduler.Implicits.global

object TestRhoRuntime {
  def rhoRuntimeEff[F[_]: Log: Metrics: Span: Concurrent: Parallel: ContextShift](
      initRegistry: Boolean = true
  ): Resource[F, (RhoRuntime[F], ReplayRhoRuntime[F], RhoHistoryRepository[F])] =
    mkRuntimes[F]("hash-set-casper-test-genesis-", initRegistry = initRegistry)
}
