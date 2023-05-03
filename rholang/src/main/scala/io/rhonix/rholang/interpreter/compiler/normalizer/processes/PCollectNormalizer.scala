package io.rhonix.rholang.interpreter.compiler.normalizer.processes

import cats.syntax.all._
import cats.effect.Sync
import io.rhonix.models.Par
import io.rhonix.models.rholang.implicits._
import io.rhonix.rholang.interpreter.compiler.{
  CollectVisitInputs,
  ProcVisitInputs,
  ProcVisitOutputs
}
import io.rhonix.rholang.ast.rholang_mercury.Absyn.PCollect
import io.rhonix.rholang.interpreter.compiler.normalizer.CollectionNormalizeMatcher

object PCollectNormalizer {
  def normalize[F[_]: Sync](p: PCollect, input: ProcVisitInputs)(
      implicit env: Map[String, Par]
  ): F[ProcVisitOutputs] =
    CollectionNormalizeMatcher
      .normalizeMatch[F](p.collection_, CollectVisitInputs(input.boundMapChain, input.freeMap))
      .map(
        collectResult =>
          ProcVisitOutputs(
            input.par.prepend(collectResult.expr, input.boundMapChain.depth),
            collectResult.freeMap
          )
      )
}
