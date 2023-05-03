package io.rhonix.rholang.interpreter.compiler.normalizer.processes

import cats.syntax.all._
import cats.effect.Sync
import io.rhonix.models.Par
import io.rhonix.models.rholang.implicits._
import io.rhonix.rholang.interpreter.compiler.{NameVisitInputs, ProcVisitInputs, ProcVisitOutputs}
import io.rhonix.rholang.ast.rholang_mercury.Absyn.PEval
import io.rhonix.rholang.interpreter.compiler.normalizer.NameNormalizeMatcher

object PEvalNormalizer {
  def normalize[F[_]: Sync](p: PEval, input: ProcVisitInputs)(
      implicit env: Map[String, Par]
  ): F[ProcVisitOutputs] =
    NameNormalizeMatcher
      .normalizeMatch[F](p.name_, NameVisitInputs(input.boundMapChain, input.freeMap))
      .map(
        nameMatchResult =>
          ProcVisitOutputs(
            input.par ++ nameMatchResult.par,
            nameMatchResult.freeMap
          )
      )
}
