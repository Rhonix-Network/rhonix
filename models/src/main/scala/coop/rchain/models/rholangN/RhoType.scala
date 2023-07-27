package coop.rchain.models.rholangN

import coop.rchain.models.rholangN.ParManager.Manager._
import coop.rchain.rspace.hashing.Blake2b256Hash
import scodec.bits.ByteVector

/** Base trait for Rholang elements in the Reducer */
sealed trait RhoTypeN {

  /** Cryptographic hash code of the element */
  lazy val rhoHash: Blake2b256Hash = rhoHashFn(this)

  /** Element size after serialization (in bytes) */
  lazy val serializedSize: Int = serializedSizeFn(this)

  /** True if the element or at least one of the nested elements non-concrete.
    * Such element cannot be viewed as if it were a term.*/
  // TODO: Rename connectiveUsed for more clarity
  lazy val connectiveUsed: Boolean = connectiveUsedFn(this)

  /** True if the element or at least one of the nested elements can be evaluate in Reducer */
  lazy val evalRequired: Boolean = evalRequiredFn(this)

  /** True if the element or at least one of the nested elements can be substitute in Reducer */
  lazy val substituteRequired: Boolean = substituteRequiredFn(this)

  override def equals(x: Any): Boolean = ParManager.Manager.equals(this, x)
}

/* TODO: In the future, it is necessary to append the classification.
         Add main types and ground types.
         Ground types must be part of expressions, and expressions are part of the main types.
 */
/** Auxiliary elements included in other pairs */
trait AuxParN extends RhoTypeN

/** Rholang element that can be processed in parallel, together with other elements */
trait ParN extends RhoTypeN {
  def toBytes: ByteVector = parToBytes(this)
}
object ParN {
  def fromBytes(bytes: ByteVector): ParN = parFromBytes(bytes)
}

/** Basic rholang operations that can be executed in parallel*/
trait BasicN extends ParN

/** Rholang unforgeable names (stored in internal environment map) */
trait UnforgeableN extends ParN { val v: ByteVector }

/** Other types that can't be categorized */
trait OtherN extends ParN

/** Expressions included in Rholang elements */
trait ExprN extends ParN

/** Base types for Rholang expressions */
trait GroundN extends ExprN

/** Rholang collections */
trait CollectionN extends ExprN

/** Variables in Rholang (can be bound, free and wildcard) */
trait VarN extends ExprN

/** Operations in Rholang */
trait OperationN extends ExprN
