package io.rhonix.casper.helper

import cats.effect.Concurrent
import io.rhonix.crypto.PublicKey
import io.rhonix.metrics.Span
import io.rhonix.models.rholang.RhoType
import io.rhonix.models.{ListParWithRandom, Par}
import io.rhonix.rholang.interpreter.ContractCall
import io.rhonix.rholang.interpreter.SystemProcesses.ProcessContext

object BlockDataContract {
  import cats.syntax.all._

  def set[F[_]: Concurrent: Span](
      ctx: ProcessContext[F]
  )(message: Seq[ListParWithRandom]): F[Unit] = {

    val isContractCall = new ContractCall(ctx.space, ctx.dispatcher)
    message match {
      case isContractCall(
          produce,
          Seq(RhoType.RhoString("sender"), RhoType.RhoByteArray(pk), ackCh)
          ) =>
        for {
          _ <- ctx.blockData.update(_.copy(sender = PublicKey(pk)))
          _ <- produce(Seq(Par()), ackCh)
        } yield ()

      case isContractCall(
          produce,
          Seq(RhoType.RhoString("blockNumber"), RhoType.RhoNumber(n), ackCh)
          ) =>
        for {
          _ <- ctx.blockData.update(_.copy(blockNumber = n))
          _ <- produce(Seq(Par()), ackCh)
        } yield ()
    }
  }
}
