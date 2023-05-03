package io.rhonix.rspace

import io.rhonix.rspace.hashing.Blake2b256Hash
import io.rhonix.rspace.trace.Produce

final case class SoftCheckpoint[C, P, A, K](
    cacheSnapshot: HotStoreState[C, P, A, K],
    log: trace.Log,
    produceCounter: Map[Produce, Int]
)
final case class Checkpoint(root: Blake2b256Hash, log: trace.Log)
