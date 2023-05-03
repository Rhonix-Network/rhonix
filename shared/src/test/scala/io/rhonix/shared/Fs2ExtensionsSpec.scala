package io.rhonix.shared

import cats.effect.concurrent.Ref
import cats.effect.{Concurrent, Timer}
import cats.syntax.all._
import io.rhonix.shared.syntax.sharedSyntaxFs2Stream
import fs2.Stream
import monix.eval.Task
import monix.execution.schedulers.TestScheduler
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.util.Success

class Fs2ExtensionsSpec extends AnyFlatSpec with Matchers {

  /**
    * Creates a Stream of 2 elements creating String "11", if timeout occurs it will insert zeroes e.g. "101"
    */
  def test[F[_]: Concurrent: Timer](timeout: FiniteDuration): F[String] = Ref.of("") flatMap { st =>
    val addOne  = Stream.eval(st.updateAndGet(_ + "1"))
    val pause   = Stream.sleep(1.second).drain
    val addZero = st.update(_ + "0")

    (addOne ++ pause ++ addOne).evalOnIdle(addZero, timeout).compile.lastOrError
  }

  // Helper to construct success result
  def success[A](a: A): Option[Success[A]] = Success(a).some

  // Instance of testing ExecutionContext (Scheduler)
  implicit val ec = TestScheduler()

  "evalOnIdle" should "NOT trigger timeout if element IS produced within timeout period" in {
    val t = test[Task](1001.millis).runToFuture

    // Sanity check, value should be empty before start
    t.value shouldBe none

    // Just before the next element produced, still no value
    ec.tick(999.millis)
    t.value shouldBe none

    ec.tick(1.millis)
    t.value shouldBe success("11")
  }

  it should "trigger timeout if element is NOT produced within timeout" in {
    val t = test[Task](750.millis).runToFuture

    // Sanity check, value should be empty before start
    t.value shouldBe none

    // Just before the next element produced, still no value
    ec.tick(999.millis)
    t.value shouldBe none

    ec.tick(1.millis)
    t.value shouldBe success("101")
  }

  it should "trigger two timeouts if element is NOT produced and timeout is double time shorter" in {
    val t = test[Task](499.millis).runToFuture

    // Sanity check, value should be empty before start
    t.value shouldBe none

    // Just before the next element produced, still no value
    ec.tick(999.millis)
    t.value shouldBe none

    ec.tick(1.millis)
    t.value shouldBe success("1001")
  }

}
