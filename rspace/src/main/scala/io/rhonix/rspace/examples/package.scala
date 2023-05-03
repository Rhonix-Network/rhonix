package io.rhonix.rspace

import io.rhonix.sdk.syntax.all._
import io.rhonix.shared.Serialize
import scodec.bits.ByteVector

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, ObjectInputStream, ObjectOutputStream}
import scala.util.Using

package object examples {

  def makeSerializeFromSerializable[T <: Serializable]: Serialize[T] =
    new Serialize[T] {

      def encode(a: T): ByteVector =
        Using.Manager { use =>
          val baos = use(new ByteArrayOutputStream())
          val oos  = use(new ObjectOutputStream(baos))
          oos.writeObject(a)
          ByteVector.view(baos.toByteArray)
        }.getUnsafe

      @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
      def decode(bytes: ByteVector): Either[Throwable, T] =
        Using.Manager { use =>
          val bais = use(new ByteArrayInputStream(bytes.toArray))
          val ois  = use(new ObjectInputStream(bais))
          ois.readObject.asInstanceOf[T]
        }.toEither
    }
}
