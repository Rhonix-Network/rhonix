package io.rhonix.casper.batch1

import cats.syntax.all._
import io.rhonix.casper.helper.TestNode
import io.rhonix.casper.helper.TestNode._
import io.rhonix.casper.util.ConstructDeploy
import io.rhonix.p2p.EffectsTestInstances.LogicalTime
import io.rhonix.shared.scalatestcontrib._
import monix.execution.Scheduler.Implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.Inspectors
import org.scalatest.matchers.should.Matchers

class MultiParentCasperSmokeSpec extends AnyFlatSpec with Matchers with Inspectors {

  import io.rhonix.casper.util.GenesisBuilder._

  implicit val timeEff = new LogicalTime[Effect]

  private val genesis = buildGenesis()

  it should "perform the most basic deploy successfully" in effectTest {
    TestNode.standaloneEff(genesis).use { node =>
      ConstructDeploy
        .sourceDeployNowF("new x in { x!(0) }", shardId = genesis.genesisBlock.shardId) >>= (node
        .addBlock(_))
    }
  }

}
