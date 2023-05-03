package io.rhonix.casper.util

import cats.FlatMap
import cats.syntax.all._
import com.google.protobuf.ByteString
import io.rhonix.casper.protocol._
import io.rhonix.casper.rholang.RuntimeManager
import io.rhonix.models.Expr.ExprInstance.GInt
import io.rhonix.models.rholang.implicits._
import io.rhonix.models.syntax._
import io.rhonix.models.{GPrivate, Par}
import io.rhonix.rholang.interpreter.{PrettyPrinter => RholangPrettyPrinter}

object RSpaceUtil {

  def getDataAtPublicChannel[F[_]: FlatMap](hash: ByteString, channel: Long)(
      implicit runtimeManager: RuntimeManager[F]
  ): F[Seq[String]] = getDataAt[F](hash, GInt(channel))

  def getDataAtPublicChannel[F[_]: FlatMap](block: BlockMessage, channel: Long)(
      implicit runtimeManager: RuntimeManager[F]
  ): F[Seq[String]] = getDataAtPublicChannel[F](block.postStateHash, channel)

  def getDataAtPrivateChannel[F[_]: FlatMap](block: BlockMessage, channel: String)(
      implicit runtimeManager: RuntimeManager[F]
  ) = {
    val name = channel.unsafeHexToByteString
    getDataAt[F](block.postStateHash, GPrivate().withId(name))
  }

  def getDataAt[F[_]: FlatMap](hash: ByteString, channel: Par)(
      implicit runtimeManager: RuntimeManager[F]
  ) =
    for {
      data <- runtimeManager.getData(hash)(channel)
      res  = data.map(_.exprs.map(RholangPrettyPrinter().buildString)).flatten
    } yield (res)

}
