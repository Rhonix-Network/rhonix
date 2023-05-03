package io.rhonix.rholang.interpreter.compiler.normalizer.processes

import cats.syntax.all._
import cats.effect.Sync
import io.rhonix.models.Expr.ExprInstance.GBool
import io.rhonix.models.{Match, MatchCase, Par}
import io.rhonix.models.rholang.implicits._
import io.rhonix.rholang.interpreter.compiler.ProcNormalizeMatcher.normalizeMatch
import io.rhonix.rholang.interpreter.compiler.{ProcVisitInputs, ProcVisitOutputs}
import io.rhonix.rholang.ast.rholang_mercury.Absyn.Proc

import scala.collection.immutable.Vector

object PIfNormalizer {
  def normalize[F[_]: Sync](
      valueProc: Proc,
      trueBodyProc: Proc,
      falseBodyProc: Proc,
      input: ProcVisitInputs
  )(
      implicit env: Map[String, Par]
  ): F[ProcVisitOutputs] =
    for {
      targetResult <- normalizeMatch[F](valueProc, input)
      trueCaseBody <- normalizeMatch[F](
                       trueBodyProc,
                       ProcVisitInputs(VectorPar(), input.boundMapChain, targetResult.freeMap)
                     )
      falseCaseBody <- normalizeMatch[F](
                        falseBodyProc,
                        ProcVisitInputs(VectorPar(), input.boundMapChain, trueCaseBody.freeMap)
                      )
      desugaredIf = Match(
        targetResult.par,
        Vector(
          MatchCase(GBool(true), trueCaseBody.par, 0),
          MatchCase(GBool(false), falseCaseBody.par, 0)
        ),
        targetResult.par.locallyFree | trueCaseBody.par.locallyFree | falseCaseBody.par.locallyFree,
        targetResult.par.connectiveUsed || trueCaseBody.par.connectiveUsed || falseCaseBody.par.connectiveUsed
      )
    } yield ProcVisitOutputs(input.par.prepend(desugaredIf), falseCaseBody.freeMap)

}
