package io.rhonix.casper.batch1

import io.rhonix.casper.helper.TestNode
import io.rhonix.casper.helper.TestNode.Effect
import io.rhonix.casper.protocol.CommEvent
import io.rhonix.casper.rholang.Resources
import io.rhonix.casper.util.ConstructDeploy
import io.rhonix.casper.reporting.{ReportStore, ReportingCasper}
import io.rhonix.models.{BindPattern, ListParWithRandom, Par, TaggedContinuation}
import io.rhonix.p2p.EffectsTestInstances.LogicalTime
import io.rhonix.rspace.ReportingRspace.ReportingComm
import io.rhonix.shared.scalatestcontrib.effectTest
import io.rhonix.store.InMemoryStoreManager
import io.rhonix.rspace.syntax._
import monix.execution.Scheduler.Implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.Inspectors
import org.scalatest.matchers.should.Matchers

class MultiParentCasperReportingSpec extends AnyFlatSpec with Matchers with Inspectors {

  import io.rhonix.casper.util.GenesisBuilder._

  implicit val timeEff: LogicalTime[Effect] = new LogicalTime[Effect]

  val genesis: GenesisContext = buildGenesis()

  "ReportingCasper" should "behave the same way as MultiParentCasper" in effectTest {
    val correctRholang =
      """ for(@a <- @"1"){ Nil } | @"1"!("x") """
    TestNode.standaloneEff(genesis).use { node =>
      import node._
      import io.rhonix.rholang.interpreter.storage._

      for {
        kvm         <- Resources.mkTestRNodeStoreManager[Effect](node.dataDir)
        rspaceStore <- kvm.rSpaceStores
        reportingCasper = ReportingCasper
          .rhoReporter[Effect](rspaceStore, this.genesis.genesisBlock.shardId)
        deploy = ConstructDeploy
          .sourceDeployNow(correctRholang, shardId = this.genesis.genesisBlock.shardId)
        signedBlock <- node.addBlock(deploy)
        _           = logEff.warns.isEmpty should be(true)
        trace       <- reportingCasper.trace(signedBlock)
        // only the comm events should be equal
        // it is possible that there are additional produce or consume in persistent mode
        reportingCommEventsNum = trace.deployReportResult.head.processedDeploy.deployLog.collect {
          case CommEvent(_, _, _) => 1
        }.sum
        deployCommEventsNum = signedBlock.state.deploys.head.deployLog.count {
          case CommEvent(_, _, _) => true
          case _                  => false
        }
        reportingReplayPostStateHash = trace.postStateHash
        _                            = reportingReplayPostStateHash shouldBe signedBlock.postStateHash
        _                            = deployCommEventsNum shouldBe reportingCommEventsNum
      } yield ()
    }
  }
}
