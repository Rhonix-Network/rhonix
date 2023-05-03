package io.rhonix.rholang.interpreter.compiler.normalizer.processes

import cats.effect.Sync
import cats.syntax.all._
import io.rhonix.models.Connective
import io.rhonix.models.Connective.ConnectiveInstance._
import io.rhonix.models.rholang.implicits._
import io.rhonix.rholang.ast.rholang_mercury.Absyn._
import io.rhonix.rholang.interpreter.compiler.{ProcVisitInputs, ProcVisitOutputs}

object PSimpleTypeNormalizer {
  def normalize[F[_]: Sync](p: PSimpleType, input: ProcVisitInputs): F[ProcVisitOutputs] =
    p.simpletype_ match {
      case _: SimpleTypeBool =>
        ProcVisitOutputs(
          input.par
            .prepend(Connective(ConnBool(true)), input.boundMapChain.depth)
            .withConnectiveUsed(true),
          input.freeMap
        ).pure[F]
      case _: SimpleTypeInt =>
        ProcVisitOutputs(
          input.par
            .prepend(Connective(ConnInt(true)), input.boundMapChain.depth)
            .withConnectiveUsed(true),
          input.freeMap
        ).pure[F]
      case _: SimpleTypeBigInt =>
        ProcVisitOutputs(
          input.par
            .prepend(Connective(ConnBigInt(true)), input.boundMapChain.depth)
            .withConnectiveUsed(true),
          input.freeMap
        ).pure[F]
      case _: SimpleTypeString =>
        ProcVisitOutputs(
          input.par
            .prepend(Connective(ConnString(true)), input.boundMapChain.depth)
            .withConnectiveUsed(true),
          input.freeMap
        ).pure[F]
      case _: SimpleTypeUri =>
        ProcVisitOutputs(
          input.par
            .prepend(Connective(ConnUri(true)), input.boundMapChain.depth)
            .withConnectiveUsed(true),
          input.freeMap
        ).pure[F]
      case _: SimpleTypeByteArray =>
        ProcVisitOutputs(
          input.par
            .prepend(Connective(ConnByteArray(true)), input.boundMapChain.depth)
            .withConnectiveUsed(true),
          input.freeMap
        ).pure[F]
    }
}
