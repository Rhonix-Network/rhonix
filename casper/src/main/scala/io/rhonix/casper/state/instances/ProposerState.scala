package io.rhonix.casper.state.instances

import cats.effect.concurrent.Deferred
import io.rhonix.casper.blocks.proposer.ProposeResult
import io.rhonix.casper.protocol.BlockMessage

final case class ProposerState[F[_]](
    latestProposeResult: Option[(ProposeResult, Option[BlockMessage])] = None,
    currProposeResult: Option[Deferred[F, (ProposeResult, Option[BlockMessage])]] = None
)
