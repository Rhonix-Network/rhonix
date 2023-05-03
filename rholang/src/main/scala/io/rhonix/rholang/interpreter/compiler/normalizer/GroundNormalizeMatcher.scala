package io.rhonix.rholang.interpreter.compiler.normalizer

import cats.effect.Sync
import cats.syntax.all._
import io.rhonix.models.Expr
import io.rhonix.models.Expr.ExprInstance.{GBigInt, GInt, GString, GUri}
import io.rhonix.rholang.ast.rholang_mercury.Absyn._
import io.rhonix.rholang.interpreter.errors.NormalizerError

object GroundNormalizeMatcher {
  def normalizeMatch[F[_]: Sync](g: Ground): F[Expr] =
    g match {
      case gb: GroundBool => Expr(BoolNormalizeMatcher.normalizeMatch(gb.boolliteral_)).pure[F]
      case gi: GroundInt =>
        Sync[F]
          .delay(gi.longliteral_.toLong)
          .adaptError { case e: NumberFormatException => NormalizerError(e.getMessage) }
          .map(long => Expr(GInt(long)))
      case gbi: GroundBigInt =>
        Sync[F]
          .delay(BigInt(gbi.longliteral_))
          .adaptError { case e: NumberFormatException => NormalizerError(e.getMessage) }
          .map(bigInt => Expr(GBigInt(bigInt)))
      case gs: GroundString => Expr(GString(stripString(gs.stringliteral_))).pure[F]
      case gu: GroundUri    => Expr(GUri(stripUri(gu.uriliteral_))).pure[F]
    }
  // This is necessary to remove the backticks. We don't use a regular
  // expression because they're always there.
  def stripUri(raw: String): String = {
    require(raw.length >= 2)
    raw.substring(1, raw.length - 1)
  }
  // Similarly, we need to remove quotes from strings, since we are using
  // a custom string token
  def stripString(raw: String): String = {
    require(raw.length >= 2)
    raw.substring(1, raw.length - 1)
  }
}
