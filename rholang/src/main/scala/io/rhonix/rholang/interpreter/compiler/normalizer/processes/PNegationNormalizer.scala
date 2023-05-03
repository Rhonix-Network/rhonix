package io.rhonix.rholang.interpreter.compiler.normalizer.processes

import cats.syntax.all._
import cats.effect.Sync
import io.rhonix.models.Connective.ConnectiveInstance.ConnNotBody
import io.rhonix.models.{Connective, Par}
import io.rhonix.models.rholang.implicits._
import io.rhonix.rholang.interpreter.compiler.ProcNormalizeMatcher.normalizeMatch
import io.rhonix.rholang.interpreter.compiler.{
  FreeMap,
  ProcVisitInputs,
  ProcVisitOutputs,
  SourcePosition
}
import io.rhonix.rholang.ast.rholang_mercury.Absyn.PNegation

object PNegationNormalizer {
  def normalize[F[_]: Sync](p: PNegation, input: ProcVisitInputs)(
      implicit env: Map[String, Par]
  ): F[ProcVisitOutputs] =
    normalizeMatch[F](
      p.proc_,
      ProcVisitInputs(VectorPar(), input.boundMapChain, FreeMap.empty)
    ).map(
      bodyResult =>
        ProcVisitOutputs(
          input.par.prepend(Connective(ConnNotBody(bodyResult.par)), input.boundMapChain.depth),
          input.freeMap
            .addConnective(
              ConnNotBody(bodyResult.par),
              SourcePosition(p.line_num, p.col_num)
            )
        )
    )
}
