package io.rhonix.casper.rholang

import shapeless.labelled.FieldType

package object sysdeploys {
  type ->>[A, B] = FieldType[A, B]
}
