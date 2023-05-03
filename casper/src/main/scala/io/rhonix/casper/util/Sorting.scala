package io.rhonix.casper.util

object Sorting {

  implicit val byteArrayOrdering = Ordering.by((_: Array[Byte]).toIterable)
}
