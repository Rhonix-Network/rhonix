package io.rhonix.comm.transport

import cats.tagless._
import io.rhonix.comm.CommError.CommErr
import io.rhonix.comm.PeerNode
import io.rhonix.comm.protocol.routing._

final case class Blob(sender: PeerNode, packet: Packet)

@autoFunctorK
@autoSemigroupalK
@autoProductNK
trait TransportLayer[F[_]] {
  def send(peer: PeerNode, msg: Protocol): F[CommErr[Unit]]
  def broadcast(peers: Seq[PeerNode], msg: Protocol): F[Seq[CommErr[Unit]]]
  def stream(peers: Seq[PeerNode], blob: Blob): F[Unit]
}

object TransportLayer {
  def apply[F[_]](implicit L: TransportLayer[F]): TransportLayer[F] = L
}
