package io.rhonix.rspace.history

import io.rhonix.rspace.hashing.Blake2b256Hash

sealed trait HistoryAction {
  def key: KeySegment
}

final case class InsertAction(key: KeySegment, hash: Blake2b256Hash) extends HistoryAction
final case class DeleteAction(key: KeySegment)                       extends HistoryAction
