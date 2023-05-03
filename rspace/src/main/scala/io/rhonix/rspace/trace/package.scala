package io.rhonix.rspace

import io.rhonix.rspace.internal.MultisetMultiMap

package object trace {

  type Log = Seq[Event]

  type ReplayData = MultisetMultiMap[IOEvent, COMM]
}
