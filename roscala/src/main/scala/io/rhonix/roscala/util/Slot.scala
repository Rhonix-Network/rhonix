package io.rhonix.roscala.util

import io.rhonix.roscala.ob.Ob
import io.rhonix.roscala.util.syntax._

class Slot extends LockedMap[Int, Ob] {

  def +=(v: Ob): Int =
    lock.writeLock().withLock {
      val size = map.size()
      map.put(size, v)
      size
    }

  def ++=(arr: Array[Ob]): Unit =
    lock.writeLock().withLock {
      val size = map.size()

      arr.zipWithIndex.foreach {
        case (v, i) =>
          map.put(i + size, v)
      }
    }
}

object Slot {
  def apply(): Slot = new Slot
}
