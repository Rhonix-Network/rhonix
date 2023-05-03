package io.rhonix.rholang.interpreter.compiler.normalizer

import io.rhonix.models.Expr.ExprInstance.GBool
import io.rhonix.rholang.ast.rholang_mercury.Absyn.{BoolFalse, BoolLiteral, BoolTrue}

object BoolNormalizeMatcher {
  def normalizeMatch(b: BoolLiteral): GBool =
    b match {
      case _: BoolTrue  => GBool(true)
      case _: BoolFalse => GBool(false)
    }
}
