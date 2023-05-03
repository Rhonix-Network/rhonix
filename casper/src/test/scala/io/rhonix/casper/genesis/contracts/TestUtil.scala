package io.rhonix.casper.genesis.contracts

import cats.FlatMap
import cats.effect.Sync
import cats.syntax.all._
import io.rhonix.crypto.hash.Blake2b512Random
import io.rhonix.models.Par
import io.rhonix.rholang.build.CompiledRholangSource
import io.rhonix.rholang.interpreter.accounting.Cost
import io.rhonix.rholang.interpreter.RhoRuntime
import io.rhonix.rholang.interpreter.compiler.Compiler

object TestUtil {
  def eval[F[_]: Sync, Env](
      source: CompiledRholangSource[Env],
      runtime: RhoRuntime[F]
  )(implicit rand: Blake2b512Random): F[Unit] = eval(source.code, runtime, source.env)

  def eval[F[_]: Sync](
      code: String,
      runtime: RhoRuntime[F],
      normalizerEnv: Map[String, Par]
  )(implicit rand: Blake2b512Random): F[Unit] =
    Compiler[F].sourceToADT(code, normalizerEnv) >>= (evalTerm(_, runtime))

  private def evalTerm[F[_]: FlatMap](
      term: Par,
      runtime: RhoRuntime[F]
  )(implicit rand: Blake2b512Random): F[Unit] =
    for {
      _ <- runtime.cost.set(Cost.UNSAFE_MAX)
      _ <- runtime.inj(term)
    } yield ()
}
