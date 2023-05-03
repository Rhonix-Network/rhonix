package io.rhonix.rholang.interpreter.compiler.normalizer.processes

import cats.syntax.all._
import cats.effect.Sync
import io.rhonix.models.rholang.implicits._
import io.rhonix.rholang.interpreter.compiler.{ProcVisitInputs, ProcVisitOutputs}
import io.rhonix.rholang.ast.rholang_mercury.Absyn.PGround
import io.rhonix.rholang.interpreter.compiler.normalizer.GroundNormalizeMatcher

object PGroundNormalizer {
  def normalize[F[_]: Sync](p: PGround, input: ProcVisitInputs): F[ProcVisitOutputs] =
    GroundNormalizeMatcher
      .normalizeMatch[F](p.ground_)
      .map(
        expr =>
          ProcVisitOutputs(
            input.par.prepend(expr, input.boundMapChain.depth),
            input.freeMap
          )
      )
}
