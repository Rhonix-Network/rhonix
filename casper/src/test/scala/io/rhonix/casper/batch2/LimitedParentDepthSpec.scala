package io.rhonix.casper.batch2

import cats.instances.list._
import cats.syntax.traverse._
import io.rhonix.casper.helper.TestNode
import io.rhonix.casper.util.ConstructDeploy.basicDeployData
import io.rhonix.casper.util.GenesisBuilder.buildGenesis
import io.rhonix.p2p.EffectsTestInstances.LogicalTime
import monix.eval.Task
import monix.execution.Scheduler
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class LimitedParentDepthSpec extends AnyFlatSpec with Matchers {
  implicit val scheduler = Scheduler.fixedPool("limited-parent-depth-scheduler", 2)
  implicit val timeEff   = new LogicalTime[Task]

  val genesisContext = buildGenesis()

  it should "obey absent parent depth limitation" in {
    TestNode.networkEff(genesisContext, networkSize = 2, maxParentDepth = None).use {
      case nodes @ n1 +: n2 +: Seq() =>
        for {
          produceDeploys <- (0 until 6).toList.traverse(i => basicDeployData[Task](i))

          b1 <- n1.propagateBlock(produceDeploys(0))()
          b2 <- n2.propagateBlock(produceDeploys(1))(nodes: _*)
          b4 <- n2.propagateBlock(produceDeploys(2))(nodes: _*)
          b4 <- n2.propagateBlock(produceDeploys(3))(nodes: _*)
          b5 <- n2.propagateBlock(produceDeploys(4))(nodes: _*)
          b6 <- n1.propagateBlock(produceDeploys(5))(nodes: _*)
        } yield b6.justifications shouldBe List(b1.blockHash, b5.blockHash)
    }
  }
}
