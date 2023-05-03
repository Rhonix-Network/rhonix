package io.rhonix.comm.rp

import cats.effect.concurrent.Ref
import cats.{catsInstancesForId => _, _}
import io.rhonix.catscontrib.effect.implicits._
import io.rhonix.catscontrib.ski._
import io.rhonix.comm.CommError._
import io.rhonix.comm._
import io.rhonix.comm.protocol.routing._
import io.rhonix.comm.rp.Connect._
import io.rhonix.metrics.Metrics
import io.rhonix.p2p.EffectsTestInstances._
import io.rhonix.shared._
import org.scalatest._
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration.{FiniteDuration, MILLISECONDS}

class ConnectSpec extends AnyFunSpec with Matchers with BeforeAndAfterEach with AppendedClues {

  val defaultTimeout: FiniteDuration = FiniteDuration(1, MILLISECONDS)
  val src: PeerNode                  = peerNode("src", 40400)
  val remote: PeerNode               = peerNode("remote", 40401)
  val networkId                      = "test"

  type Effect[A] = CommErrT[Id, A]

  implicit val logEff            = new Log.NOPLog[Effect]
  implicit val timeEff           = new LogicalTime[Effect]
  implicit val metricEff         = new Metrics.MetricsNOP[Effect]
  implicit val nodeDiscoveryEff  = new NodeDiscoveryStub[Effect]()
  implicit val transportLayerEff = new TransportLayerStub[Effect]
  implicit val connectionsCell   = Ref.unsafe[Effect, Connections](Connect.Connections.empty)
  implicit val rpConfAsk         = createRPConfAsk[Effect](peerNode("src", 40400))

  override def beforeEach(): Unit = {
    nodeDiscoveryEff.reset()
    transportLayerEff.reset()
  }

  describe("Node") {
    describe("when connecting to other remote node") {
      it("should send ProtocolHandshake") {
        // given
        transportLayerEff.setResponses(kp(alwaysSuccess))
        // when
        Connect.connect[Effect](remote)
        // then
        transportLayerEff.requests.size should be(1)
        val Protocol(_, Protocol.Message.ProtocolHandshake(_)) = transportLayerEff.requests(0).msg
      }
    }

    describe("when reciving encrypted ProtocolHandshake") {
      it("should send protocol handshake response back to the remote")(pending)
      it("should add node once protocol handshake response is sent")(pending)
      it("should not respond if message can not be decrypted")(pending)
      it("should not respond if it does not contain remotes public key")(pending)
    }

  }

  def alwaysSuccess: Protocol => CommErr[Unit] = kp(Right(()))

  private def endpoint(port: Int): Endpoint = Endpoint("host", port, port)
  private def peerNode(name: String, port: Int): PeerNode =
    PeerNode(NodeIdentifier(name.getBytes), endpoint(port))

}
