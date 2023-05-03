package io.rhonix.comm.transport

import io.rhonix.comm.PeerNode
import io.rhonix.comm.protocol.routing.Protocol

trait ServerMessage

final case class Send(msg: Protocol) extends ServerMessage

final case class StreamMessage(
    sender: PeerNode,
    typeId: String,
    key: String,
    compressed: Boolean,
    contentLength: Int
) extends ServerMessage
