package io.rhonix.crypto.util

import java.security.interfaces.ECPublicKey
import java.security.spec.{ECGenParameterSpec, ECParameterSpec, ECPoint}
import java.security.{AlgorithmParameters, PublicKey}

import io.rhonix.crypto.util.CertificateHelper._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class CertificateHelperTest extends AnyFlatSpec with Matchers {
  val dummyPc = new PublicKey {
    override def getAlgorithm: String    = ???
    override def getFormat: String       = ???
    override def getEncoded: Array[Byte] = ???
  }

  val ap = AlgorithmParameters.getInstance("EC")
  ap.init(new ECGenParameterSpec(EllipticCurveName))

  val goodKey = ecPk(ap.getParameterSpec(classOf[ECParameterSpec]))

  def ecPk(ecParameterSpec: ECParameterSpec) = new ECPublicKey {
    override def getW: ECPoint              = new ECPoint(BigInt(0).bigInteger, BigInt(0).bigInteger)
    override def getAlgorithm: String       = ???
    override def getFormat: String          = ???
    override def getEncoded: Array[Byte]    = ???
    override def getParams: ECParameterSpec = ecParameterSpec
  }

  "isExpectedEllipticCurve" should "return false" in {
    isExpectedEllipticCurve(dummyPc) shouldBe false
  }

  "isExpectedEllipticCurve" should "return true" in {

    isExpectedEllipticCurve(goodKey) shouldBe true
  }

  "publicAddress" should "return None" in {
    publicAddress(dummyPc) shouldBe None
  }

  "publicAddress" should "return some address" in {
    publicAddress(goodKey) should not be empty
  }
}
