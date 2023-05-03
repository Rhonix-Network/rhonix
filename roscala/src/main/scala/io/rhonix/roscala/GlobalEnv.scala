package io.rhonix.roscala

import io.rhonix.roscala.ob.{Ob, TblObject}

class GlobalEnv extends TblObject {
  def values(n: Int): Ob = extension.slot.unsafeGet(n)

  override def numberOfSlots: Int = extension.slot.size
}
