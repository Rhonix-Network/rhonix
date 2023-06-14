package io.rhonix.node.benchmark

import cats.Parallel
import cats.effect.concurrent.Ref
import cats.effect.{Blocker, Concurrent, ContextShift}
import cats.syntax.all._
import fs2.Stream
import io.circe.syntax._
import io.rhonix.casper.genesis.Genesis
import io.rhonix.casper.genesis.contracts.Validator
import io.rhonix.casper.protocol.{CommEvent, ConsumeEvent, ProduceEvent}
import io.rhonix.casper.rholang.{BlockRandomSeed, RuntimeManager}
import io.rhonix.casper.{StatefulExecutionTracker, ValidatorIdentity}
import io.rhonix.crypto.signatures.Secp256k1
import io.rhonix.metrics.Metrics.Source
import io.rhonix.metrics.{Metrics, Span}
import io.rhonix.node.benchmark.utils.GenesisParams.genesisParameters
import io.rhonix.node.benchmark.utils.LeaderfulSimulation.ValidatorWithPayments
import io.rhonix.node.benchmark.utils.{Payment, StateTransition, User}
import io.rhonix.rspace.syntax._
import io.rhonix.shared.syntax._
import io.rhonix.shared.{Log, Stopwatch, Time}
import io.rhonix.store.InMemoryStoreManager
import monix.eval.Task
import monix.execution.Scheduler

import java.nio.file.Path

/** Benchmark for concurrent state transitions. This is equivalent to concurrent block validation. */
object ConcurrentReplayBench {

  def main(args: Array[String]): Unit = {
    implicit val time                 = Time.fromTimer[Task]
    implicit val c                    = Concurrent[Task]
    implicit val scheduler: Scheduler = Scheduler.Implicits.global
    implicit val log                  = Log.log

    go[Task](100, Path.of("bench")).runSyncUnsafe()
  }

  def go[F[_]: Concurrent: Parallel: Time: Log: ContextShift](
      stateTransitionsMax: Int,
      dataDir: Path
  )(implicit scheduler: Scheduler): F[Unit] = {
    implicit val m = new Metrics.MetricsNOP[F]

    // run everything in memory
    val rnodeStoreManager = new InMemoryStoreManager

    for {
      rSpaceStores <- rnodeStoreManager.rSpaceStores
      // extract all performance data gathered by Span trait usage across codebase
      statsRef <- Ref.of[F, Map[String, (Long, Int)]](Map.empty)
      profiler = new Span[F] {
        override def trace[A](source: Source)(block: F[A]): F[A] =
          for {
            v         <- Stopwatch.durationRaw(block)
            (r, time) = v
            _ <- statsRef.update { s =>
                  val (currTimeAcc, currCallsAcc) = s.getOrElse(source, (0L, 0))
                  val newV                        = (currTimeAcc + time.toNanos, currCallsAcc + 1)
                  s.updated(source, newV)
                }
          } yield r

        // do not need these one
        override def mark(name: String): F[Unit]                    = ().pure[F]
        override def withMarks[A](label: String)(block: F[A]): F[A] = block
      }

      mergeStore <- RuntimeManager.mergeableStore(rnodeStoreManager)
      // Block execution tracker
      executionTracker <- StatefulExecutionTracker[F]
      runtimeManager <- {
        implicit val span = profiler
        RuntimeManager(
          rSpaceStores,
          mergeStore,
          BlockRandomSeed.nonNegativeMergeableTagName("dummy"),
          executionTracker
        )
      }

      _     <- Log[F].info(s"Preparing genesis block...")
      users = User.random.take(stateTransitionsMax).toList
      validatorsKeys = (1 to stateTransitionsMax)
        .map(_ => Secp256k1.newKeyPair)
        .map { case (_, pk) => pk }
        .toList
      genesisVaults = users.map(_.pk)
      bondedValidators = validatorsKeys.zipWithIndex.map {
        case (v, i) =>
          Validator(v, 2L * i.toLong + 1L)
      }
      genesis <- {
        implicit val a = runtimeManager
        Genesis.createGenesisBlock(
          ValidatorIdentity(Secp256k1.newKeyPair._1),
          genesisParameters(bondedValidators, genesisVaults)
        )
      }
      _ <- Log[F].info(s"Genesis done.")

      r <- {
        implicit val rm = runtimeManager
        implicit val blocker =
          Blocker.liftExecutionContext(scala.concurrent.ExecutionContext.global)

        // first is a warm up for JVM
        (Stream(1, 1, 2, 3, 4, 5, 7) ++ Stream.range(10, stateTransitionsMax + 5, 5))
          .evalMap { networkSize =>
            val validators = validatorsKeys.take(networkSize)
            val payments   = Payment.random(users, 1, 10).take(networkSize).toList
            val transitions = validators
              .zip(payments)
              .map { case (v, p) => ValidatorWithPayments(v, Seq(p)) }

            val test = Stream
              .emits(transitions)
              .parEvalMapProcBounded { v =>
                StateTransition.make(
                  genesis.postStateHash,
                  v.validator,
                  0,
                  1,
                  v.payments.toList
                )
              }
              .compile
              .toList

            for {
              _               <- Log[F].info(s"Running ${transitions.size} concurrent STs.")
              r               <- Stopwatch.durationRaw(test)
              (results, time) = r
              timeStr         = Stopwatch.showTime(time)
              avgDeploysPerST = results.flatMap(_.processedDeploys).size.toFloat / results.size
              ps = results
                .flatMap(_.processedDeploys.flatMap(_.deployLog))
                .collect { case c: ProduceEvent => c }
                .size
              cs = results
                .flatMap(_.processedDeploys.flatMap(_.deployLog))
                .collect { case c: ConsumeEvent => c }
                .size
              comms = results
                .flatMap(_.processedDeploys.flatMap(_.deployLog))
                .collect { case c: CommEvent => c }
                .size
              psSys = results
                .flatMap(_.processedSysDeploys.flatMap(_.eventList))
                .collect { case c: ProduceEvent => c }
                .size
              csSys = results
                .flatMap(_.processedSysDeploys.flatMap(_.eventList))
                .collect { case c: ConsumeEvent => c }
                .size
              commsSys = results
                .flatMap(_.processedSysDeploys.flatMap(_.eventList))
                .collect { case c: CommEvent => c }
                .size
              cps   = comms.toFloat / time.toNanos * 1e9
              stats <- statsRef.get
              logMsg = stats.toList
                .sortBy { case (_, (v, _)) => v }
                .reverse
                .foldLeft(
                  s"\nDONE: ${results.size} State transitions (avg $avgDeploysPerST TX per ST), " +
                    s"user events: $ps P, $cs C $comms COMM, " +
                    s"sys events : $psSys P, $csSys C $commsSys COMM, " +
                    s"time: ${timeStr}. COMMS per sec: ${cps}"
                ) {
                  case (acc, (metric, (totalTime, totalCalls))) =>
                    val timeS = totalTime.toFloat / 1e9
                    acc + f"\n$metric%60s: avg ${timeS / totalCalls}%.7f s, total $timeS%.7f s, calls ${totalCalls}, "
                }
              _ <- Log[F].info(logMsg)
              _ <- statsRef.set(Map.empty) // reset stats
              r = stats.map {
                case (metric, (total, qty)) =>
                  metric -> ("size" -> networkSize, "total" -> total, "calls" -> qty)
              }.asJson
            } yield r
          }
          .flatMap(v => Stream.fromIterator(v.show.getBytes.iterator))
          .through(fs2.io.file.writeAll(dataDir.resolve("out.json"), blocker))
          .compile
          .lastOrError
      }
    } yield ()
  }
}
