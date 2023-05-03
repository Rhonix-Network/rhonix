package io.rhonix.node.runtime

import cats.effect.Sync
import cats.syntax.all._
import io.rhonix.comm.NodeIdentifier
import io.rhonix.comm.transport.GenerateCertificateIfAbsent
import io.rhonix.crypto.util.CertificateHelper
import io.rhonix.models.syntax._
import io.rhonix.node.configuration.NodeConf
import io.rhonix.shared.Log

import java.io.File
import java.security.cert.X509Certificate

object NodeEnvironment {

  class InitializationException(msg: String) extends RuntimeException

  def create[F[_]: Sync: Log](conf: NodeConf): F[NodeIdentifier] =
    for {
      dataDir <- Sync[F].delay(conf.storage.dataDir.toFile)
      _       <- canCreateDataDir(dataDir)
      _       <- haveAccessToDataDir(dataDir)
      _       <- Log[F].info(s"Using data dir: ${dataDir.getAbsolutePath}")
      _       <- GenerateCertificateIfAbsent.run(conf.tls)
      _       <- hasCertificate(conf)
      _       <- hasKey(conf)
      name    <- name(conf)
    } yield NodeIdentifier(name)

  private def name[F[_]: Sync](conf: NodeConf): F[String] = {
    val certificate: F[X509Certificate] =
      Sync[F]
        .fromTry(CertificateHelper.fromFile(conf.tls.certificatePath.toFile))
        .adaptError {
          case ex =>
            new InitializationException(s"Failed to read the X.509 certificate: ${ex.getMessage}")
        }

    for {
      cert <- certificate
      pk   = cert.getPublicKey
      publicAddress <- CertificateHelper.publicAddress(pk).liftTo[F] {
                        new InitializationException(
                          "Certificate must contain a secp256r1 EC Public Key"
                        )
                      }
    } yield publicAddress.toHexString
  }

  private def canCreateDataDir[F[_]: Sync](dataDir: File): F[Unit] =
    runtimeError(
      s"The data dir must be a directory and have read and write permissions:\\n${dataDir.getAbsolutePath}"
    ).whenA(!dataDir.exists() && !dataDir.mkdir())

  private def haveAccessToDataDir[F[_]: Sync](dataDir: File): F[Unit] =
    runtimeError(
      s"The data dir must be a directory and have read and write permissions:\n${dataDir.getAbsolutePath}"
    ).whenA(!dataDir.isDirectory || !dataDir.canRead || !dataDir.canWrite)

  private def hasCertificate[F[_]: Sync](conf: NodeConf): F[Unit] =
    runtimeError(
      s"Certificate file ${conf.tls.certificatePath} not found"
    ).whenA(!conf.tls.certificatePath.toFile.exists())

  private def hasKey[F[_]: Sync](conf: NodeConf): F[Unit] =
    runtimeError(
      s"Secret key file ${conf.tls.certificatePath} not found"
    ).whenA(!conf.tls.keyPath.toFile.exists())

  private def runtimeError[F[_]: Sync](msg: String): F[Unit] =
    Sync[F].raiseError(new RuntimeException(msg))
}
