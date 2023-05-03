package io.rhonix.roscala.ob.mbox

import io.rhonix.roscala.ob._

import scala.collection.mutable

class MboxQueue(elems: Tuple) extends Queue(mutable.Queue(elems.value: _*)) {

  def isEmpty: Boolean = queue.isEmpty

  def maybeDequeue(enabledSet: Ob): Ob =
    queue.dequeueFirst(msg => enabledSet.accepts(msg.asInstanceOf[Ctxt])).getOrElse(Invalid)
}

object MboxQueue {
  def apply(): MboxQueue = new MboxQueue(Tuple())
}
