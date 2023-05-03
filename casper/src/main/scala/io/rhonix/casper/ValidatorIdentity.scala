package io.rhonix.casper

import cats.Applicative
import cats.syntax.all._
import io.rhonix.casper.protocol.BlockMessage
import io.rhonix.casper.util.ProtoUtil
import io.rhonix.crypto.signatures.{Secp256k1, SignaturesAlg}
import io.rhonix.crypto.{PrivateKey, PublicKey}
import io.rhonix.models.syntax._
import io.rhonix.shared.{Log, LogSource}

final case class ValidatorIdentity(
    publicKey: PublicKey,
    privateKey: PrivateKey,
    sigAlgorithm: String
) {
  // TODO: handle Option.none result with descriptive exception
  def signature(data: Array[Byte]): Array[Byte] =
    SignaturesAlg(sigAlgorithm).map(_.sign(data, privateKey)).get

  // TODO: remove hashing as part of signing
  def signBlock(block: BlockMessage): BlockMessage = {
    val blockHash = ProtoUtil.hashBlock(block)

    val sig = signature(blockHash.toByteArray).toByteString

    block.copy(sig = sig, blockHash = blockHash)
  }
}

object ValidatorIdentity {
  implicit private val logSource: LogSource = LogSource(this.getClass)

  def apply(privateKey: PrivateKey): ValidatorIdentity = {
    val publicKey = Secp256k1.toPublic(privateKey)

    ValidatorIdentity(publicKey, privateKey, Secp256k1.name)
  }

  def fromHex(privKeyHex: String): Option[ValidatorIdentity] =
    privKeyHex.decodeHex.map(PrivateKey(_)).map(ValidatorIdentity(_))

  def fromPrivateKeyWithLogging[F[_]: Applicative: Log](
      privKey: Option[String]
  ): F[Option[ValidatorIdentity]] =
    privKey
      .map(fromHex)
      .fold(
        Log[F]
          .warn("No private key detected, cannot create validator identification.")
          .as(none[ValidatorIdentity])
      )(_.pure)
}
