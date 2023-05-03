package io.rhonix.rholang.interpreter

import io.rhonix.metrics
import io.rhonix.metrics.{Metrics, NoopSpan, Span}
import io.rhonix.models.{BindPattern, ListParWithRandom, Par, TaggedContinuation}
import io.rhonix.rholang.Resources.mkRhoISpace
import io.rhonix.rholang.interpreter.RhoRuntime.RhoISpace
import io.rhonix.rholang.interpreter.accounting.{Cost, CostAccounting}
import io.rhonix.rholang.interpreter.storage._
import io.rhonix.rspace.RSpace
import io.rhonix.rspace.syntax.rspaceSyntaxKeyValueStoreManager
import io.rhonix.shared.Log
import io.rhonix.store.InMemoryStoreManager
import monix.eval.Task
import monix.execution.Scheduler.Implicits.global

import scala.concurrent.duration._

final case class TestFixture(space: RhoISpace[Task], reducer: DebruijnInterpreter[Task])

trait PersistentStoreTester {
  implicit val ms: Metrics.Source = Metrics.BaseSource

  def withTestSpace[R](f: TestFixture => R): R = {
    implicit val logF: Log[Task]           = new Log.NOPLog[Task]
    implicit val metricsEff: Metrics[Task] = new metrics.Metrics.MetricsNOP[Task]
    implicit val noopSpan: Span[Task]      = NoopSpan[Task]()

    implicit val cost = CostAccounting.emptyCost[Task].runSyncUnsafe()
    implicit val m    = matchListPar[Task]
    implicit val kvm  = InMemoryStoreManager[Task]
    val store         = kvm.rSpaceStores.runSyncUnsafe()
    val space = RSpace
      .create[Task, Par, BindPattern, ListParWithRandom, TaggedContinuation](store)
      .runSyncUnsafe()
    val reducer = RholangOnlyDispatcher(space)._2
    cost.set(Cost.UNSAFE_MAX).runSyncUnsafe(1.second)

    // Execute test
    f(TestFixture(space, reducer))
  }

  def fixture[R](f: (RhoISpace[Task], Reduce[Task]) => Task[R]): R = {
    implicit val logF: Log[Task]           = new Log.NOPLog[Task]
    implicit val metricsEff: Metrics[Task] = new metrics.Metrics.MetricsNOP[Task]
    implicit val noopSpan: Span[Task]      = NoopSpan[Task]()
    implicit val kvm                       = InMemoryStoreManager[Task]
    mkRhoISpace[Task]
      .flatMap {
        case rspace =>
          for {
            cost <- CostAccounting.emptyCost[Task]
            reducer = {
              implicit val c = cost
              RholangOnlyDispatcher(rspace)._2
            }
            _   <- cost.set(Cost.UNSAFE_MAX)
            res <- f(rspace, reducer)
          } yield res
      }
      .runSyncUnsafe(3.seconds)
  }
}
