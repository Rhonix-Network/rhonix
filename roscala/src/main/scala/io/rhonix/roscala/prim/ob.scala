package io.rhonix.roscala.prim

import io.rhonix.roscala.ob.{Ctxt, Fixnum}

object ob {
  object objectIndexedSize extends Prim {
    override val name: String = "prim-size"
    override val minArgs: Int = 1
    override val maxArgs: Int = 1

    override def fnSimple(ctxt: Ctxt): Either[PrimError, Fixnum] =
      Right(ctxt.arg(0).indexedSize())
  }
}
