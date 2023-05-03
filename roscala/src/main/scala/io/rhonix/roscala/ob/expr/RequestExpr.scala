package io.rhonix.roscala.ob.expr

import io.rhonix.roscala.ob.Ob

class RequestExpr(elem: Seq[Ob], rest: Option[Ob] = None) extends Expr {
  def numberOfElements(): Int = elem.size
}
