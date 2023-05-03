package io.rhonix.casper.helper

import cats.effect.Concurrent
import cats.syntax.all._
import io.rhonix.blockstorage.BlockStore.BlockStore
import io.rhonix.blockstorage.dag.BlockDagStorage
import io.rhonix.casper.api.BlockApiImpl
import io.rhonix.casper.rholang.RuntimeManager
import io.rhonix.casper.{StatefulExecutionTracker, ValidatorIdentity}
import io.rhonix.comm.rp.Connect.Connection
import io.rhonix.comm.{Endpoint, NodeIdentifier, PeerNode}
import io.rhonix.metrics.Span
import io.rhonix.shared.Log

trait BlockApiFixture {

  def createBlockApi[F[_]: Concurrent: RuntimeManager: BlockDagStorage: BlockStore: Log: Span](
      shardId: String,
      maxDepthLimit: Int,
      validatorIdOpt: Option[ValidatorIdentity] = none
  ): F[BlockApiImpl[F]] = {
    val thisNode = peerNode("testNode", 1234)
    for {
      executionTracker <- StatefulExecutionTracker[F]
      blockApi <- BlockApiImpl[F](
                   validatorOpt = validatorIdOpt,
                   networkId = "rhonix",
                   shardId = shardId,
                   minPhloPrice = 1,
                   version = "",
                   (thisNode, List[Connection](), Seq[PeerNode]()).pure[F],
                   isNodeReadOnly = validatorIdOpt.isEmpty,
                   maxDepthLimit = maxDepthLimit,
                   devMode = false,
                   triggerPropose = none,
                   proposerStateRefOpt = none,
                   autoPropose = false,
                   executionTracker = executionTracker
                 )
    } yield blockApi
  }

  def createBlockApi[F[_]: Concurrent](node: TestNode[F]): F[BlockApiImpl[F]] = {
    import node.{blockDagStorage, blockStore, logEff, runtimeManager, sp}

    val thisNode = node.local
    for {
      executionTracker <- StatefulExecutionTracker[F]
      blockApi <- BlockApiImpl[F](
                   validatorOpt = node.validatorIdOpt,
                   networkId = "rhonix",
                   shardId = node.shardName,
                   minPhloPrice = 1,
                   version = "",
                   (thisNode, List[Connection](), Seq[PeerNode]()).pure[F],
                   isNodeReadOnly = node.validatorIdOpt.isEmpty,
                   maxDepthLimit = node.apiMaxBlocksLimit,
                   devMode = false,
                   triggerPropose = none,
                   proposerStateRefOpt = none,
                   autoPropose = false,
                   executionTracker = executionTracker
                 )
    } yield blockApi
  }

  protected def endpoint(port: Int): Endpoint = Endpoint("host", port, port)

  protected def peerNode(name: String, port: Int): PeerNode =
    PeerNode(NodeIdentifier(name.getBytes), endpoint(port))
}
