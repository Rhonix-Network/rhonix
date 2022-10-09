package io.rhonix.crypto.hash

import io.rhonix.shared.Base16
import org.scalatest.{AppendedClues, BeforeAndAfterEach}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class Keccak256Test extends AnyFunSpec with Matchers with BeforeAndAfterEach with AppendedClues {
  describe("Keccak256 hashing algorithm") {
    it("encodes empty") {
      val result = Base16.encode(Keccak256.hash("".getBytes))
      result shouldBe "c5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470"
    }
    it("encodes data") {
      val result = Base16.encode(Keccak256.hash("abc".getBytes))
      result shouldBe "4e03657aea45a94fc7d47ba826c8d667c0d1e6e33a64a036ec44f58fa12d6c45"
    }
  }

}
