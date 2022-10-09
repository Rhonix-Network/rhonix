package io.rhonix.node.dag

import cats.effect.{Concurrent, Sync}
import cats.effect.concurrent.Ref
import cats.syntax.all._
import io.rhonix.node.dag.implementation.{BlockStatus, NetworkBlockRequester, RNodeDagManager}

object RNodeStateSetup {

  /**
    * TODO: Should create the whole node state to be used for API, CLI, etc.
    */
  def setupRNodeState[F[_]: Concurrent, M, MId, S, SId] =
    for {
      /* State */

      // Block requester state
      blockReqSt <- Ref.of(Map[MId, BlockStatus[M, MId]]())

      // DAG Manager state
      dagMngrSt <- Ref.of(Map[MId, M]())

      requester <- NetworkBlockRequester[F, M, MId](blockReqSt)

      dagMngr <- RNodeDagManager[F, M, MId, S, SId](
                  dagMngrSt,
                  requester.requestBlock,
                  requester.response
                )
    } yield dagMngr
}
