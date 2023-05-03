package io.rhonix.casper

import cats.effect.Sync
import cats.effect.concurrent.Ref
import cats.syntax.all._
import io.rhonix.blockstorage.dag.BlockDagStorage.DeployId
import io.rhonix.rholang.interpreter.EvaluateResult
import io.rhonix.sdk.syntax.all._

trait BlockExecutionTracker[F[_]] {
  def execStarted(d: DeployId): F[Unit]
  def execComplete(d: DeployId, res: EvaluateResult): F[Unit]
}

sealed trait DeployStatus
case object DeployStatusStarted                    extends DeployStatus
final case class DeployStatusError(status: String) extends DeployStatus

object StatefulExecutionTracker {
  def apply[F[_]: Sync]: F[StatefulExecutionTracker[F]] =
    for {
      ref <- Ref.of(Map.empty[DeployId, DeployStatus])
    } yield new StatefulExecutionTracker(ref)
}

final class StatefulExecutionTracker[F[_]: Sync](state: Ref[F, Map[DeployId, DeployStatus]])
    extends BlockExecutionTracker[F] {

  override def execStarted(d: DeployId): F[Unit] = state.update(_ + (d -> DeployStatusStarted))
  override def execComplete(d: DeployId, res: EvaluateResult): F[Unit] =
    state
      .update(_ + (d -> DeployStatusError {
        // If deploy fails update status with errors
        res.errors.map(_.getMessageSafe).mkString("\n")
      }))
      .whenA(res.failed)

  def findDeploy(d: DeployId): F[Option[DeployStatus]] = state.get.map(_.get(d))
}
