package io.rhonix.rspace.bench

import cats.Id
import io.rhonix.metrics
import io.rhonix.metrics.{Metrics, NoopSpan, Span}
import io.rhonix.rholang.interpreter.RholangCLI
import io.rhonix.rspace.examples.AddressBookExample._
import io.rhonix.rspace.examples.AddressBookExample.implicits._
import io.rhonix.rspace.syntax.rspaceSyntaxKeyValueStoreManager
import io.rhonix.rspace.{RSpace, _}
import io.rhonix.shared.Log
import io.rhonix.shared.PathOps.RichPath
import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole

import java.nio.file.{Files, Path}
import java.util.concurrent.TimeUnit

class ReplayRSpaceBench {

  import ReplayRSpaceBench._

  @Benchmark
  @BenchmarkMode(Array(Mode.SingleShotTime))
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  @Warmup(iterations = 1)
  @Fork(value = 1)
  @Measurement(iterations = 1)
  def singleProduce(bh: Blackhole, state: ProduceInMemBenchState) = {
    val res = state.replaySpace.produce(state.produceChannel, bob, persist = true)
    assert(res.isDefined)
    bh.consume(res)
  }

  @Benchmark
  @BenchmarkMode(Array(Mode.SingleShotTime))
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  @Warmup(iterations = 1)
  @Fork(value = 1)
  @Measurement(iterations = 1)
  def singleConsume(bh: Blackhole, state: ConsumeInMemBenchState) = {
    val res = state.replaySpace.consume(
      List(state.consumeChannel),
      state.matches,
      state.captor,
      persist = true
    )
    assert(res.isDefined)
    bh.consume(res)
  }
}

object ReplayRSpaceBench {

  import scala.concurrent.ExecutionContext.Implicits.global

  abstract class ReplayRSpaceBenchState {
    var space: ISpace[Id, Channel, Pattern, Entry, EntriesCaptor] = null
    var replaySpace: IReplaySpace[cats.Id, Channel, Pattern, Entry, EntriesCaptor] =
      null
    implicit val logF: Log[Id]            = new Log.NOPLog[Id]
    implicit val noopMetrics: Metrics[Id] = new metrics.Metrics.MetricsNOP[Id]
    implicit val noopSpan: Span[Id]       = NoopSpan[Id]()
    val consumeChannel                    = Channel("consume")
    val produceChannel                    = Channel("produce")
    val matches                           = List(CityMatch(city = "Crystal Lake"))
    val captor                            = new EntriesCaptor()

    def initSpace() = {
      val rigPoint = space.createCheckpoint()
      replaySpace.rigAndReset(rigPoint.root, rigPoint.log)
    }

    private var dbDir: Path = null

    @Setup
    def setup() = {
      dbDir = Files.createTempDirectory("replay-rspace-bench-")
      val kvm   = RholangCLI.mkRSpaceStoreManager[Id](dbDir)
      val store = kvm.rSpaceStores
      val (space, replaySpace) =
        RSpace.createWithReplay[Id, Channel, Pattern, Entry, EntriesCaptor](store)
      this.space = space
      this.replaySpace = replaySpace
    }

    @TearDown
    def tearDown() = {
      dbDir.recursivelyDelete()
      ()
    }
  }

  @State(Scope.Thread)
  class ConsumeInMemBenchState extends ReplayRSpaceBenchState {

    def prepareConsume() = {
      (1 to 1000).foreach { _ =>
        space.produce(consumeChannel, bob, persist = true)

      }
      (1 to 2).foreach { i =>
        space.consume(
          List(consumeChannel),
          matches,
          captor,
          persist = true
        )
      }
    }
    @Setup
    override def setup() = {
      super.setup()
      prepareConsume()
      initSpace
    }
  }

  @State(Scope.Thread)
  class ProduceInMemBenchState extends ReplayRSpaceBenchState {

    def prepareProduce() = {
      (1 to 1000).foreach { _ =>
        space.consume(
          List(produceChannel),
          matches,
          captor,
          persist = true
        )
      }
      (1 to 2).foreach { i =>
        space.produce(produceChannel, bob, persist = true)
      }
    }
    @Setup
    override def setup() = {
      super.setup()
      prepareProduce()
      initSpace
    }
  }
}
