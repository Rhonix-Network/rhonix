package io.rhonix.rholang.interpreter.compiler.normalizer.processes

import cats.syntax.all._
import cats.effect.Sync
import io.rhonix.models.{EMatches, Par}
import io.rhonix.models.rholang.implicits._
import io.rhonix.rholang.interpreter.compiler.ProcNormalizeMatcher.normalizeMatch
import io.rhonix.rholang.interpreter.compiler.{FreeMap, ProcVisitInputs, ProcVisitOutputs}
import io.rhonix.rholang.ast.rholang_mercury.Absyn.PMatches

object PMatchesNormalizer {
  def normalize[F[_]: Sync](p: PMatches, input: ProcVisitInputs)(
      implicit env: Map[String, Par]
  ): F[ProcVisitOutputs] =
    // In case of 'matches' expression the free variables from the pattern are thrown away
    // and only the ones from the target are used.
    // This is because the "target matches pattern" should have the same semantics as
    // "match target { pattern => true ; _ => false}
    // so free variables from pattern should not be visible at the top level
    for {
      leftResult <- normalizeMatch[F](p.proc_1, input.copy(par = VectorPar()))
      rightResult <- normalizeMatch[F](
                      p.proc_2,
                      ProcVisitInputs(
                        VectorPar(),
                        input.boundMapChain.push,
                        FreeMap.empty
                      )
                    )
    } yield ProcVisitOutputs(
      input.par.prepend(EMatches(leftResult.par, rightResult.par), input.boundMapChain.depth),
      leftResult.freeMap
    )
}
