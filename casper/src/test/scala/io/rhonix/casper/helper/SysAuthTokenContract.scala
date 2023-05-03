package io.rhonix.casper.helper

import cats.effect.Concurrent
import io.rhonix.metrics.Span
import io.rhonix.models.rholang.RhoType
import io.rhonix.models.{GSysAuthToken, ListParWithRandom}
import io.rhonix.rholang.interpreter.ContractCall
import io.rhonix.rholang.interpreter.SystemProcesses.ProcessContext

/**
  * Warning: This should under no circumstances be available in production
  */
object SysAuthTokenContract {
  import cats.syntax.all._

  def get[F[_]: Concurrent: Span](
      ctx: ProcessContext[F]
  )(message: Seq[ListParWithRandom]): F[Unit] = {

    val isContractCall = new ContractCall(ctx.space, ctx.dispatcher)
    message match {
      case isContractCall(
          produce,
          Seq(ackCh)
          ) =>
        for {
          _ <- produce(Seq(RhoType.RhoSysAuthToken(GSysAuthToken())), ackCh)
        } yield ()
    }
  }
}
