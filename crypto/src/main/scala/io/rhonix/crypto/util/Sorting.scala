package io.rhonix.crypto.util

import io.rhonix.crypto.PublicKey
import io.rhonix.shared.Base16

object Sorting {

  implicit val byteArrayOrdering = Ordering.by((_: Array[Byte]).toIterable)

  implicit val publicKeyOrdering: Ordering[PublicKey] = Ordering.by[PublicKey, Array[Byte]](_.bytes)

}
