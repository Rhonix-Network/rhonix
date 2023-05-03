package io.rhonix.rholang.interpreter

import io.rhonix.models.Expr.ExprInstance.GInt
import io.rhonix.models.{Par, ReceiveBind, Var}
import io.rhonix.models.Var.VarInstance.FreeVar
import monix.eval.Coeval
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.rhonix.models.rholang.implicits._
import io.rhonix.rholang.interpreter.compiler.{FreeMap, ReceiveBindsSortMatcher, VarSort}

class ReceiveSortMatcherSpec extends AnyFlatSpec with Matchers {
  val emptyMap = FreeMap.empty[VarSort]
  "Binds" should "Presort based on their channel and then pattern" in {
    val binds: List[(List[Par], Option[Var], Par, FreeMap[VarSort])] =
      List(
        (
          List(GInt(2)),
          None,
          GInt(3),
          emptyMap
        ),
        (
          List(GInt(3)),
          None,
          GInt(2),
          emptyMap
        ),
        (
          List(GInt(3)),
          Some(FreeVar(0)),
          GInt(2),
          emptyMap
        ),
        (
          List(GInt(1)),
          None,
          GInt(3),
          emptyMap
        )
      )
    val sortedBinds: List[Tuple2[ReceiveBind, FreeMap[VarSort]]] =
      List(
        (
          ReceiveBind(
            List(GInt(3)),
            GInt(2),
            None
          ),
          emptyMap
        ),
        (
          ReceiveBind(
            List(GInt(3)),
            GInt(2),
            Some(FreeVar(0))
          ),
          emptyMap
        ),
        (
          ReceiveBind(
            List(GInt(1)),
            GInt(3),
            None
          ),
          emptyMap
        ),
        (
          ReceiveBind(
            List(GInt(2)),
            GInt(3),
            None
          ),
          emptyMap
        )
      )
    val result = ReceiveBindsSortMatcher.preSortBinds[Coeval, VarSort](binds).value
    result should be(sortedBinds)
  }
}
