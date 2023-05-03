package io.rhonix.rspace.bench

import io.rhonix.crypto.hash.Blake2b512Random
import io.rhonix.metrics
import io.rhonix.metrics.{Metrics, NoopSpan, Span}
import io.rhonix.models.Par
import io.rhonix.rholang.Resources
import io.rhonix.rholang.interpreter.RholangCLI
import io.rhonix.rholang.interpreter.compiler.Compiler
import io.rhonix.rspace.syntax.rspaceSyntaxKeyValueStoreManager
import io.rhonix.shared.Log
import monix.eval.{Coeval, Task}
import monix.execution.Scheduler.Implicits.global
import org.openjdk.jmh.annotations.{Setup, TearDown}

import java.io.{FileNotFoundException, InputStreamReader}
import java.nio.file.{Files, Path}

trait EvalBenchStateBase {
  private lazy val dbDir: Path            = Files.createTempDirectory("rhonix-storage-test-")
  implicit val logF: Log[Task]            = new Log.NOPLog[Task]
  implicit val noopMetrics: Metrics[Task] = new metrics.Metrics.MetricsNOP[Task]
  implicit val noopSpan: Span[Task]       = NoopSpan[Task]()
  implicit val kvm                        = RholangCLI.mkRSpaceStoreManager[Task](dbDir).runSyncUnsafe()

  val rhoScriptSource: String

  val store                       = kvm.rSpaceStores.runSyncUnsafe()
  lazy val spaces                 = Resources.createRuntimes[Task](store).runSyncUnsafe()
  val (runtime, replayRuntime, _) = spaces

  val rand: Blake2b512Random = Blake2b512Random.defaultRandom
  var term: Option[Par]      = None

  @Setup
  def doSetup(): Unit = {
    deleteOldStorage(dbDir)

    term = Compiler[Coeval].sourceToADT(resourceFileReader(rhoScriptSource)).runAttempt match {
      case Right(par) => Some(par)
      case Left(err)  => throw err
    }
  }

  @TearDown
  def tearDown(): Unit = ()

  def resourceFileReader(path: String): InputStreamReader =
    new InputStreamReader(
      Option(getClass.getResourceAsStream(path))
        .getOrElse(throw new FileNotFoundException(path))
    )
}
