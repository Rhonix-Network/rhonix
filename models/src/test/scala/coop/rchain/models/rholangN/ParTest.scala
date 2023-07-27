package coop.rchain.models.rholangN

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

class ParSpec extends AnyFlatSpec with ScalaCheckPropertyChecks with Matchers {

  def simpleCheck(p1: ParN, p2Opt: Option[ParN] = None): Boolean = {
    val bytes1        = p1.toBytes
    val recover1      = ParN.fromBytes(bytes1)
    val res1: Boolean = p1.rhoHash == recover1.rhoHash
    val res2: Boolean = if (p2Opt.isDefined) {
      val p2       = p2Opt.get
      val bytes2   = p2.toBytes
      (p1.rhoHash == p2.rhoHash) && (bytes1 == bytes2)
    } else true
    res1 && res2
  }

  behavior of "Par"

  /** Main types */
  it should "test ParProc" in {
    val p1 = ParProcN(Seq(GNilN(), ParProcN()))
    val p2 = ParProcN(Seq(ParProcN(),GNilN()))
    simpleCheck (p1, Some(p2)) should be (true)
  }

  it should "test Send with same data order" in {
    val p1 = SendN(GNilN(), Seq(GNilN(), SendN(GNilN(), GNilN())), persistent = true)
    simpleCheck(p1) should be(true)
  }

  it should "test Send with different data order" in {
    val p1 = SendN(GNilN(), Seq(GNilN(), SendN(GNilN(), GNilN())), persistent = true)
    val p2 = SendN(GNilN(), Seq(SendN(GNilN(), GNilN()), GNilN()), persistent = true)
    simpleCheck(p1, Some(p2)) should be(false)
  }

  /** Ground types */
  it should "test GNil" in {
    val p = GNilN()
    simpleCheck(p) should be(true)
  }

  it should "test GInt" in {
    val p = GIntN(42)
    simpleCheck(p) should be(true)
  }

  /** Collections */
  it should "test EList with same data order" in {
    val p = EListN(Seq(GNilN(), EListN()))
    simpleCheck(p) should be(true)
  }

  it should "test EList with different data order" in {
    val p1 = EListN(Seq(GNilN(), EListN()))
    val p2 = EListN(Seq(EListN(), GNilN()))
    simpleCheck(p1, Some(p2)) should be(false)
  }

  /** Vars */

  it should "test BoundVar" in {
    val p = BoundVarN(42)
    simpleCheck(p) should be(true)
  }

  it should "test FreeVar" in {
    val p = FreeVarN(42)
    simpleCheck(p) should be(true)
  }

  it should "test Wildcard" in {
    val p = WildcardN()
    simpleCheck(p) should be(true)
  }

  /** Expr */
  /** Bundle */
  /** Connective */

}
