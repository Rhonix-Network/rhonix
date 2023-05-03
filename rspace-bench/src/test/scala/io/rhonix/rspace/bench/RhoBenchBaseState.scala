package io.rhonix.rspace.bench

import io.rhonix.crypto.hash.Blake2b512Random
import io.rhonix.metrics
import io.rhonix.metrics.{Metrics, NoopSpan, Span}
import io.rhonix.models.Par
import io.rhonix.rholang.Resources
import io.rhonix.rholang.interpreter.compiler.Compiler
import io.rhonix.rholang.interpreter.{ReplayRhoRuntime, RhoRuntime, RholangCLI}
import io.rhonix.rspace.syntax.rspaceSyntaxKeyValueStoreManager
import io.rhonix.shared.Log
import monix.eval.{Coeval, Task}
import monix.execution.Scheduler
import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole

import java.nio.file.{Files, Path}
import scala.concurrent.Await
import scala.concurrent.duration._

abstract class RhoBenchBaseState {

  def setupRho: Option[String] = None
  def testedRho: String

  def execute(bh: Blackhole): Unit = {
    val r = (for {
      result <- runTask
      _      <- runtime.createCheckpoint
    } yield result).runSyncUnsafe()
    bh.consume(r)
  }

  implicit val scheduler: Scheduler = Scheduler.fixedPool(name = "rho-1", poolSize = 100)
  lazy val dbDir: Path              = Files.createTempDirectory(BenchStorageDirPrefix)

  var runtime: RhoRuntime[Task]             = null
  var replayRuntime: ReplayRhoRuntime[Task] = null
  var setupTerm: Option[Par]                = None
  var term: Par                             = _
  var randSetup: Blake2b512Random           = null
  var randRun: Blake2b512Random             = null

  var runTask: Task[Unit] = null

  implicit val logF: Log[Task]            = Log.log[Task]
  implicit val noopMetrics: Metrics[Task] = new metrics.Metrics.MetricsNOP[Task]
  implicit val noopSpan: Span[Task]       = NoopSpan[Task]()
  implicit val ms: Metrics.Source         = Metrics.BaseSource
  def rand: Blake2b512Random              = Blake2b512Random.defaultRandom

  def createRuntime =
    for {
      kvm                         <- RholangCLI.mkRSpaceStoreManager[Task](dbDir)
      store                       <- kvm.rSpaceStores
      spaces                      <- Resources.createRuntimes[Task](store)
      (runtime, replayRuntime, _) = spaces
    } yield (runtime, replayRuntime)

  @Setup(value = Level.Iteration)
  def doSetup(): Unit = {
    deleteOldStorage(dbDir)
    setupTerm = setupRho.flatMap { p =>
      Compiler[Coeval].sourceToADT(p).runAttempt match {
        case Right(par) => Some(par)
        case Left(err)  => throw err
      }
    }

    term = Compiler[Coeval].sourceToADT(testedRho).runAttempt match {
      case Right(par) => par
      case Left(err)  => throw err
    }

    val runtimes = createRuntime.runSyncUnsafe()
    runtime = runtimes._1
    replayRuntime = runtimes._2
    randSetup = rand
    randRun = rand
    Await
      .result(
        createTest(setupTerm)(runtime, randSetup).runToFuture,
        Duration.Inf
      )
    runTask = createTest(Some(term))(runtime, randRun)
  }

  @TearDown
  def tearDown(): Unit = ()
}
