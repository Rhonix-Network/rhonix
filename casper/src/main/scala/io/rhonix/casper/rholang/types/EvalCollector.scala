package io.rhonix.casper.rholang.types

import io.rhonix.casper.protocol.Event
import io.rhonix.models.Par

final case class EvalCollector(eventLog: Vector[Event], mergeableChannels: Set[Par]) {
  def addEventLog(log: Seq[Event]): EvalCollector =
    copy(eventLog = eventLog ++ log)

  def addMergeableChannels(mergeChs: Set[Par]): EvalCollector =
    copy(mergeableChannels = mergeableChannels ++ mergeChs)

  def add(log: Seq[Event], mergeChs: Set[Par]): EvalCollector =
    copy(eventLog ++ log, mergeableChannels ++ mergeChs)
}

object EvalCollector {
  def apply(): EvalCollector = EvalCollector(Vector(), Set())
}
