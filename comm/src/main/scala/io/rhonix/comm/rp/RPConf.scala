package io.rhonix.comm.rp

import scala.concurrent.duration._

import io.rhonix.comm.PeerNode

final case class RPConf(
    local: PeerNode,
    networkId: String,
    bootstrap: Option[PeerNode],
    defaultTimeout: FiniteDuration,
    maxNumOfConnections: Int,
    clearConnections: ClearConnectionsConf
)

final case class ClearConnectionsConf(numOfConnectionsPinged: Int)
