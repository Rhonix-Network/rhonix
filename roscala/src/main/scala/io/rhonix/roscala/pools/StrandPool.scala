package io.rhonix.roscala.pools

import io.rhonix.roscala.Vm.State
import io.rhonix.roscala.ob.Ctxt

trait StrandPool {
  type Task = (Ctxt, State)

  def append(task: Task): Unit

  def prepend(task: Task): Unit

  def getNextStrand(state: State): Boolean

  def finish(): Unit
}
