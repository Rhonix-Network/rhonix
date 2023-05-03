package io.rhonix.casper.helper

import cats.effect.Concurrent
import io.rhonix.crypto.signatures.Secp256k1
import io.rhonix.metrics.Span
import io.rhonix.models.ListParWithRandom
import io.rhonix.models.rholang.RhoType
import io.rhonix.rholang.interpreter.{ContractCall, SystemProcesses}

object Secp256k1SignContract {

  def get[F[_]: Concurrent: Span](
      ctx: SystemProcesses.ProcessContext[F]
  )(message: Seq[ListParWithRandom]): F[Unit] = {
    val isContractCall = new ContractCall(ctx.space, ctx.dispatcher)
    message match {
      case isContractCall(
          produce,
          Seq(RhoType.RhoByteArray(hash), RhoType.RhoByteArray(sk), ackCh)
          ) =>
        val sig = Secp256k1.sign(hash, sk)
        produce(Seq(RhoType.RhoByteArray(sig)), ackCh)
    }
  }
}
