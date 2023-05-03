package io.rhonix.rholang.interpreter.compiler.normalizer.processes

import cats.syntax.all._
import cats.effect.Sync
import io.rhonix.models.Connective.ConnectiveInstance.ConnAndBody
import io.rhonix.models.{Connective, ConnectiveBody, Par}
import io.rhonix.models.rholang.implicits._
import io.rhonix.rholang.interpreter.compiler.ProcNormalizeMatcher.normalizeMatch
import io.rhonix.rholang.interpreter.compiler.{ProcVisitInputs, ProcVisitOutputs, SourcePosition}
import io.rhonix.rholang.ast.rholang_mercury.Absyn.PConjunction

import scala.collection.immutable.Vector

object PConjunctionNormalizer {
  def normalize[F[_]: Sync](p: PConjunction, input: ProcVisitInputs)(
      implicit env: Map[String, Par]
  ): F[ProcVisitOutputs] =
    for {
      leftResult <- normalizeMatch[F](
                     p.proc_1,
                     ProcVisitInputs(VectorPar(), input.boundMapChain, input.freeMap)
                   )
      rightResult <- normalizeMatch[F](
                      p.proc_2,
                      ProcVisitInputs(VectorPar(), input.boundMapChain, leftResult.freeMap)
                    )
      lp = leftResult.par
      resultConnective = lp.singleConnective() match {
        case Some(Connective(ConnAndBody(ConnectiveBody(ps)))) =>
          Connective(ConnAndBody(ConnectiveBody(ps :+ rightResult.par)))
        case _ =>
          Connective(ConnAndBody(ConnectiveBody(Vector(lp, rightResult.par))))
      }
    } yield ProcVisitOutputs(
      input.par.prepend(resultConnective, input.boundMapChain.depth),
      rightResult.freeMap
        .addConnective(
          resultConnective.connectiveInstance,
          SourcePosition(p.line_num, p.col_num)
        )
    )
}
