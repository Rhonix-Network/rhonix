package io.rhonix.casper.genesis.contracts
import io.rhonix.casper.helper.RhoSpec
import io.rhonix.models.NormalizerEnv
import io.rhonix.rholang.build.CompiledRholangSource
import org.scalatest.AppendedClues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._
import monix.execution.Scheduler.Implicits.global

class TimeoutResultCollectorSpec extends AnyFlatSpec with AppendedClues with Matchers {
  it should "testFinished should be false if execution hasn't finished within timeout" in {
    new RhoSpec(
      CompiledRholangSource("TimeoutResultCollectorTest.rho", NormalizerEnv.Empty),
      Seq.empty,
      10.seconds
    ).result.hasFinished should be(false)
  }
}
