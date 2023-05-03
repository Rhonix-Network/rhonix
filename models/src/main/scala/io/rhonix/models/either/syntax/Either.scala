package io.rhonix.models.either.syntax

import cats.effect.Sync
import cats.implicits._

import io.rhonix.casper.protocol.ServiceError
import io.rhonix.models.StacksafeMessage

trait EitherSyntax {

  implicit final def modelEitherSyntaxGrpModel[F[_]: Sync, R <: StacksafeMessage[R], A](
      response: F[R]
  ): ScalaEitherServiceErrorOps[F, R, A] =
    new ScalaEitherServiceErrorOps(response)
}

final class ScalaEitherServiceErrorOps[F[_]: Sync, R <: StacksafeMessage[R], A](response: F[R]) {
  def toEitherF(
      error: R => Option[ServiceError],
      result: R => Option[A]
  ): F[Either[Seq[String], A]] =
    response.flatMap { r =>
      error(r)
        .map(_.messages.asLeft[A].pure[F])
        .orElse(result(r).map(_.asRight[Seq[String]].pure[F]))
        .getOrElse(Sync[F].raiseError(new RuntimeException("Response is empty")))
    }
}
