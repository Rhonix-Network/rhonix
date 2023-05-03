package io.rhonix.comm.discovery

import cats.syntax.all._
import cats.Monad

import io.rhonix.comm.PeerNode
import io.rhonix.metrics.Metrics

object KademliaHandleRPC {
  implicit private val metricsSource: Metrics.Source = DiscoveryMetricsSource

  def handlePing[F[_]: Monad: KademliaStore: Metrics](peer: PeerNode): F[Unit] =
    Metrics[F]
      .incrementCounter("handle.ping") >> KademliaStore[F]
      .updateLastSeen(peer)

  def handleLookup[F[_]: Monad: KademliaStore: Metrics](
      peer: PeerNode,
      id: Array[Byte]
  ): F[Seq[PeerNode]] =
    Metrics[F]
      .incrementCounter("handle.lookup") >> KademliaStore[F]
      .updateLastSeen(peer) >> KademliaStore[F]
      .lookup(id)
}
