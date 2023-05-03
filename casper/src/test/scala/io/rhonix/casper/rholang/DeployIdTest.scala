package io.rhonix.casper.rholang

import cats.effect.Resource
import cats.syntax.all._
import cats.implicits.catsSyntaxApplicativeId
import io.rhonix.casper.genesis.Genesis
import io.rhonix.casper.helper.TestNode
import io.rhonix.casper.protocol.DeployData
import io.rhonix.casper.syntax._
import io.rhonix.casper.rholang.Resources._
import io.rhonix.casper.util.GenesisBuilder.buildGenesis
import io.rhonix.casper.util.{ConstructDeploy, ProtoUtil}
import io.rhonix.crypto.PrivateKey
import io.rhonix.crypto.signatures.Signed
import io.rhonix.models.Expr.ExprInstance.GBool
import io.rhonix.models.rholang.implicits._
import io.rhonix.models.{GDeployId, Par}
import io.rhonix.shared.Log
import io.rhonix.shared.scalatestcontrib._
import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._

class DeployIdTest extends AnyFlatSpec with Matchers {
  implicit val log: Log[Task]    = new Log.NOPLog[Task]()
  private val dummyMergeableName = BlockRandomSeed.nonNegativeMergeableTagName("dummy")

  private val runtimeManager: Resource[Task, RuntimeManager[Task]] =
    mkRuntimeManager[Task]("deploy-id-runtime-manager-test", dummyMergeableName)

  private val sk = ConstructDeploy.defaultSec

  private def deploy(
      deployer: PrivateKey,
      rho: String,
      timestamp: Long = System.currentTimeMillis(),
      shardId: String
  ): Signed[DeployData] = ConstructDeploy.sourceDeploy(
    source = rho,
    timestamp = System.currentTimeMillis(),
    sec = deployer,
    shardId = shardId
  )

  "Deploy id" should "be equal to deploy signature" in {
    val d = deploy(
      sk,
      s"""new return, deployId(`rho:rhonix:deployId`) in { return!(*deployId) }""",
      shardId = genesisContext.genesisBlock.shardId
    )
    val result =
      runtimeManager
        .use(
          mgr =>
            for {
              hash <- RuntimeManager.emptyStateHashFixed.pure[Task]
              res  <- mgr.spawnRuntime >>= { _.captureResults(hash, d) }
            } yield res
        )
        .runSyncUnsafe(10.seconds)

    result.size should be(1)
    result.head should be(GDeployId(d.sig): Par)
  }

  val genesisContext = buildGenesis()

  it should "be resolved during normalization" in effectTest {
    val contract = deploy(
      sk,
      s"""contract @"check"(input, ret) = { new deployId(`rho:rhonix:deployId`) in { ret!(*input == *deployId) }}""",
      shardId = genesisContext.genesisBlock.shardId
    )
    val contractCall = deploy(
      sk,
      s"""new return, deployId(`rho:rhonix:deployId`), ret in { @"check"!(*deployId, *return) }""",
      shardId = genesisContext.genesisBlock.shardId
    )

    TestNode.standaloneEff(genesisContext).use { node =>
      for {
        block <- node.addBlock(contract)
        result <- node.runtimeManager.spawnRuntime >>= {
                   _.captureResults(block.postStateHash, contractCall)
                 }
        _ = assert(result.size == 1)
        _ = assert(result.head == (GBool(false): Par))
      } yield ()
    }
  }
}
