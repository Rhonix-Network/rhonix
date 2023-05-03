package io.rhonix.rspace.state.instances

import cats.effect.Concurrent
import cats.syntax.all._
import io.rhonix.rspace.hashing.Blake2b256Hash
import io.rhonix.rspace.history.RootsStoreInstances
import io.rhonix.rspace.history.instances.RadixHistory
import io.rhonix.rspace.state.RSpaceExporter
import io.rhonix.shared.ByteVectorOps.RichByteVector
import io.rhonix.shared.syntax._
import io.rhonix.state.TrieNode
import io.rhonix.store.KeyValueStore

import java.nio.ByteBuffer

object RSpaceExporterStore {
  // RSpace exporter constructor / smart constructor "guards" private class
  def apply[F[_]: Concurrent](
      historyStore: KeyValueStore[F],
      valueStore: KeyValueStore[F],
      rootsStore: KeyValueStore[F]
  ): RSpaceExporter[F] = RSpaceExporterImpl(historyStore, valueStore, rootsStore)

  final case object NoRootError extends Exception

  private final case class RSpaceExporterImpl[F[_]: Concurrent](
      sourceHistoryStore: KeyValueStore[F],
      sourceValueStore: KeyValueStore[F],
      sourceRootsStore: KeyValueStore[F]
  ) extends RSpaceExporter[F] {
    import RSpaceExporter._
    import cats.instances.tuple._

    def getItems[Value](
        store: KeyValueStore[F],
        keys: Seq[Blake2b256Hash],
        fromBuffer: ByteBuffer => Value
    ): F[Seq[(Blake2b256Hash, Value)]] =
      for {
        loaded <- store.get(keys.map(_.bytes.toDirectByteBuffer), fromBuffer)
      } yield keys.zip(loaded).filter(_._2.nonEmpty).map(_.map(_.get))

    override def getHistoryItems[Value](
        keys: Seq[Blake2b256Hash],
        fromBuffer: ByteBuffer => Value
    ): F[Seq[(KeyHash, Value)]] = getItems(sourceHistoryStore, keys, fromBuffer)

    override def getDataItems[Value](
        keys: Seq[Blake2b256Hash],
        fromBuffer: ByteBuffer => Value
    ): F[Seq[(KeyHash, Value)]] = getItems(sourceValueStore, keys, fromBuffer)

    override def getNodes(
        startPath: NodePath,
        skip: Int,
        take: Int
    ): F[Seq[TrieNode[Blake2b256Hash]]] = {
      val sourceTrieStore = RadixHistory.createStore(sourceHistoryStore)
      for {
        nodes <- traverseHistory(startPath, skip, take, sourceTrieStore.get1)
      } yield nodes
    }

    override def getRoot: F[Blake2b256Hash] = {
      val rootsStore = RootsStoreInstances.rootsStore(sourceRootsStore)
      for {
        maybeRoot <- rootsStore.currentRoot()
        root      <- maybeRoot.liftTo(NoRootError)
      } yield root
    }
  }
}
