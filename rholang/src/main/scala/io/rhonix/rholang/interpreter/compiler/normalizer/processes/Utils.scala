package io.rhonix.rholang.interpreter.compiler.normalizer.processes

import cats.syntax.all._
import io.rhonix.models.Connective.ConnectiveInstance.{ConnNotBody, ConnOrBody}
import io.rhonix.rholang.interpreter.compiler.{NameVisitOutputs, ProcVisitInputs}
import io.rhonix.rholang.interpreter.errors.{InterpreterError, PatternReceiveError}

object Utils {
  def failOnInvalidConnective(
      input: ProcVisitInputs,
      nameRes: NameVisitOutputs
  ): Either[InterpreterError, NameVisitOutputs] =
    if (input.boundMapChain.depth == 0) {
      Either
        .fromOption(
          nameRes.freeMap.connectives
            .collectFirst {
              case (_: ConnOrBody, sourcePosition) =>
                PatternReceiveError(s"\\/ (disjunction) at $sourcePosition")
              case (_: ConnNotBody, sourcePosition) =>
                PatternReceiveError(s"~ (negation) at $sourcePosition")
            },
          nameRes
        )
        .swap
    } else Right(nameRes)
}
