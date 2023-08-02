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
sealed trait ParN extends RhoTypeN {
  def toBytes: ByteVector      = parToBytes(this)
  def compare(that: ParN): Int = comparePars(this, that)

  /** Combine two pars for their parallel execution */
  def combine(that: ParN): ParN = combinePars(this, that)
}
object ParN {
  def fromBytes(bytes: ByteVector): ParN = parFromBytes(bytes)

  /**
    * Create a flatten parallel Par (ParProc) from par sequence.
    * See [[flattedPProc]] for more information.
    */
  def makeParProc(ps: Seq[ParN]): ParN = flattedPProc(ps)
}

/** Basic rholang operations that can be executed in parallel*/
trait BasicN extends ParN

/** Expressions included in Rholang elements */
sealed trait ExprN extends ParN

/** Base types for Rholang expressions */
trait GroundN extends ExprN

/** Rholang collections */
trait CollectionN extends ExprN

/** Variables in Rholang (can be bound, free and wildcard) */
trait VarN extends ExprN

/** Operations in Rholang */
sealed trait OperationN extends ExprN

/** Operation with one par */
trait Operation1ParN extends OperationN {
  val p: ParN
}

/** Operation with two par */
trait Operation2ParN extends OperationN {
  val p1: ParN
  val p2: ParN
}

/** Other operations (e.g. method) */
trait OperationOtherN extends OperationN

/** Rholang unforgeable names (stored in internal environment map) */
trait UnforgeableN extends ParN {
  val v: ByteVector
}

/**
  * Connectives (bindings) are used in patterns to combine several conditions together or
  * to set a pattern with some specific Rholang type or variables.
  * */
trait ConnectiveN extends ParN

/** Connectives for simple types */
trait ConnectiveSTypeN extends ConnectiveN

/** Connectives for truth-functional operators */
trait ConnectiveFuncN extends ConnectiveN

/** Connectives for variables */
trait ConnectiveVarN extends ConnectiveN

/** Other types that can't be categorized */
trait OtherN extends ParN
