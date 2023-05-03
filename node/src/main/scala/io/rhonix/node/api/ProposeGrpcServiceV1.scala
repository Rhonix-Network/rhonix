package io.rhonix.node.api

import cats.effect.Sync
import cats.syntax.all._
import io.rhonix.casper.api.BlockApi
import io.rhonix.casper.protocol.propose.v1.{
  ProposeResponse,
  ProposeResultResponse,
  ProposeServiceV1GrpcMonix
}
import io.rhonix.casper.protocol.{ProposeQuery, ProposeResultQuery, ServiceError}
import io.rhonix.catscontrib.TaskContrib._
import io.rhonix.models.StacksafeMessage
import io.rhonix.monix.Monixable
import io.rhonix.shared.ThrowableOps._
import io.rhonix.shared._
import io.rhonix.shared.syntax._
import monix.eval.Task
import monix.execution.Scheduler

object ProposeGrpcServiceV1 {

  def apply[F[_]: Monixable: Sync: Log](
      blockApi: BlockApi[F]
  )(
      implicit worker: Scheduler
  ): ProposeServiceV1GrpcMonix.ProposeService =
    new ProposeServiceV1GrpcMonix.ProposeService {

      private def defer[A, R <: StacksafeMessage[R]](
          task: F[Either[String, A]]
      )(
          response: Either[ServiceError, A] => R
      ): Task[R] =
        task.toTask
          .executeOn(worker)
          .fromTask
          .logOnError("Propose service method error.")
          .attempt
          .map(
            _.fold(
              t => response(ServiceError(t.toMessageList()).asLeft),
              r => response(r.leftMap(e => ServiceError(Seq(e))))
            )
          )
          .toTask

      // This method should return immediately, only trggerred propose if allowed
      def propose(
          request: ProposeQuery
      ): Task[ProposeResponse] =
        defer(blockApi.createBlock(request.isAsync)) { r =>
          import ProposeResponse.Message
          import ProposeResponse.Message._
          ProposeResponse(r.fold[Message](Error, Result))
        }

      // This method waits for propose to finish, returning result data
      def proposeResult(request: ProposeResultQuery): Task[ProposeResultResponse] =
        defer(blockApi.getProposeResult) { r =>
          import ProposeResultResponse.Message
          import ProposeResultResponse.Message._
          ProposeResultResponse(r.fold[Message](Error, Result))
        }
    }
}
