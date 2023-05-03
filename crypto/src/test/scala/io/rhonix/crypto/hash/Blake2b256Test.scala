package io.rhonix.crypto.hash

import io.rhonix.crypto.codec._
import io.rhonix.shared.Base16
import org.scalatest.{AppendedClues, BeforeAndAfterEach}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class Blake2b256Test extends AnyFunSpec with Matchers with BeforeAndAfterEach with AppendedClues {
  describe("Blake2b256 hashing algorithm") {
    it("encodes empty") {
      val result = Base16.encode(Blake2b256.hash("".getBytes))
      result shouldBe "0e5751c026e543b2e8ab2eb06099daa1d1e5df47778f7787faab45cdf12fe3a8"
    }
    it("encodes data") {
      val result = Base16.encode(Blake2b256.hash("abc".getBytes))
      result shouldBe "bddd813c634239723171ef3fee98579b94964e3bb1cb3e427262c8c068d52319"
    }
  }
}
