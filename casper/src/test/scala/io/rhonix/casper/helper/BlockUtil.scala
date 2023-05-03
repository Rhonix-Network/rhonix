package io.rhonix.casper.helper

import com.google.protobuf.ByteString
import io.rhonix.casper.protocol.BlockMessage
import io.rhonix.casper.util.ProtoUtil.hashBlock
import io.rhonix.crypto.PrivateKey
import io.rhonix.crypto.signatures.SignaturesAlg
import io.rhonix.models.BlockHash.BlockHash
import io.rhonix.models.{BlockHash, Validator}

import scala.util.Random

object BlockUtil {
  def resignBlock(b: BlockMessage, sk: PrivateKey): BlockMessage = {
    def signFunction: (Array[Byte], PrivateKey) => Array[Byte] =
      SignaturesAlg(b.sigAlgorithm).get.sign

    val blockHash =
      hashBlock(b)
    val sig = ByteString.copyFrom(signFunction(blockHash.toByteArray, sk))
    b.copy(blockHash = blockHash, sig = sig)
  }

  def generateValidator(prefix: String = ""): ByteString = {
    val array = Array.ofDim[Byte](Validator.Length)
    Random.nextBytes(array)
    ByteString.copyFrom(array)
  }

  def generateHash(prefix: String = ""): BlockHash = {
    val array = Array.ofDim[Byte](BlockHash.Length)
    Random.nextBytes(array)
    ByteString.copyFrom(array)
  }
}
