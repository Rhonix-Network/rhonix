package coop.rchain.comm.rp

import cats._
import cats.effect._
import cats.syntax.all._
import coop.rchain.catscontrib.ski._
import coop.rchain.comm.CommError._
import coop.rchain.comm._
import coop.rchain.comm.protocol.routing._
import coop.rchain.comm.rp.Connect.Connections._
import coop.rchain.comm.rp.Connect.{ConnectionsCell, RPConfAsk}
import coop.rchain.comm.transport.CommunicationResponse._
import coop.rchain.comm.transport._
import coop.rchain.metrics.Metrics
import coop.rchain.shared._
import fs2.concurrent.Queue
import monix.eval.Task

import java.net.InetAddress
import scala.util.Try

object HandleMessages {

  implicit private val logSource: LogSource = LogSource(this.getClass)
  implicit private val metricsSource: Metrics.Source =
    Metrics.Source(CommMetricsSource, "rp.handle")

  def handle[F[_]: Sync: TransportLayer: ConnectionsCell: RPConfAsk: Log: Metrics](
      protocol: Protocol,
      routingMessageQueue: Queue[F, RoutingMessage]
  ): F[CommunicationResponse] =
    handle_[F](protocol, ProtocolHelper.sender(protocol), routingMessageQueue)

  private def handle_[F[_]: Sync: TransportLayer: ConnectionsCell: RPConfAsk: Log: Metrics](
      proto: Protocol,
      sender: PeerNode,
      routingMessageQueue: Queue[F, RoutingMessage]
  ): F[CommunicationResponse] =
    proto.message match {
      case Protocol.Message.Heartbeat(heartbeat) => handleHeartbeat[F](sender, heartbeat)
      case Protocol.Message.ProtocolHandshake(protocolhandshake) =>
        handleProtocolHandshake[F](sender, protocolhandshake)
      case Protocol.Message.ProtocolHandshakeResponse(_) =>
        handleProtocolHandshakeResponse[F](sender)
      case Protocol.Message.Disconnect(disconnect) => handleDisconnect[F](sender, disconnect)
      case Protocol.Message.Packet(packet)         => handlePacket[F](sender, packet, routingMessageQueue)
      case msg =>
        Log[F].error(s"Unexpected message type $msg") >> notHandled(unexpectedMessage(msg.toString))
          .pure[F]
    }

  def handleDisconnect[F[_]: Sync: TransportLayer: ConnectionsCell: Log: Metrics](
      sender: PeerNode,
      disconnect: Disconnect
  ): F[CommunicationResponse] =
    for {
      _ <- Log[F].info(s"Forgetting about ${sender.toAddress}.")
      _ <- ConnectionsCell[F].removeConnAndReport(sender)
      _ <- Metrics[F].incrementCounter("disconnect")
    } yield handledWithoutMessage

  def handlePacket[F[_]: Functor](
      remote: PeerNode,
      packet: Packet,
      routingMessageQueue: Queue[F, RoutingMessage]
  ): F[CommunicationResponse] =
    routingMessageQueue.enqueue1(RoutingMessage(remote, packet)).as(handledWithoutMessage)

  def handleProtocolHandshakeResponse[F[_]: Monad: TransportLayer: ConnectionsCell: RPConfAsk: Log: Metrics](
      peer: PeerNode
  ): F[CommunicationResponse] =
    for {
      _ <- Log[F].debug(s"Received protocol handshake response from $peer.")
      _ <- ConnectionsCell[F].addConnAndReport(peer)
    } yield handledWithoutMessage

  def handleProtocolHandshake[F[_]: Monad: TransportLayer: Log: ConnectionsCell: RPConfAsk: Metrics](
      peer: PeerNode,
      protocolHandshake: ProtocolHandshake
  ): F[CommunicationResponse] = {
    def isValidPublicInetAddress(host: String): Boolean =
      Try(InetAddress.getByName(host))
        .map { addr =>
          !(addr.isAnyLocalAddress ||
            addr.isLinkLocalAddress ||
            addr.isLoopbackAddress ||
            addr.isMulticastAddress ||
            addr.isSiteLocalAddress)
        }
        .getOrElse(false)

    def canAddNewHost(conf: RPConf): F[Boolean] = {
      val isCurrentPeerLocal  = isValidPublicInetAddress(conf.local.endpoint.host)
      val isIncomingPeerLocal = isValidPublicInetAddress(peer.endpoint.host)
      val bothPeersAreLocal   = isCurrentPeerLocal && isIncomingPeerLocal
      val bothPeersArePublic  = !isCurrentPeerLocal && !isIncomingPeerLocal

      (bothPeersAreLocal || bothPeersArePublic).pure[F]
    }

    def acceptConnection(conf: RPConf): F[CommunicationResponse] = {
      val response = ProtocolHelper.protocolHandshakeResponse(conf.local, conf.networkId)
      for {
        resErr <- TransportLayer[F].send(peer, response)
        _ <- resErr.fold(
              kp(().pure[F]),
              kp(
                Log[F].info(s"Responded to protocol handshake request from $peer") >>
                  ConnectionsCell[F].addConnAndReport(peer)
              )
            )
      } yield handledWithoutMessage
    }

    def declineConnection: F[CommunicationResponse] =
      Log[F]
        .info(s"Unable to add connection: incoming address ${peer.toAddress}")
        .as(handledWithoutMessage)

    RPConfAsk[F].ask
      .flatMap(conf => canAddNewHost(conf).ifM(acceptConnection(conf), declineConnection))
  }

  def handleHeartbeat[F[_]: Monad: ConnectionsCell](
      peer: PeerNode,
      heartbeat: Heartbeat
  ): F[CommunicationResponse] =
    ConnectionsCell[F]
      .update(_.refreshConn(peer))
      .map(kp(handledWithoutMessage))
}
