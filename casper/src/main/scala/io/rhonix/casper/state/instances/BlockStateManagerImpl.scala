package io.rhonix.casper.state.instances

import cats.effect.Sync
import cats.syntax.all._
import io.rhonix.blockstorage.BlockStore.BlockStore
import io.rhonix.blockstorage.syntax._
import io.rhonix.blockstorage.dag.BlockDagStorage
import io.rhonix.casper.state.BlockStateManager

object BlockStateManagerImpl {
  def apply[F[_]: Sync](
      blockStore: BlockStore[F],
      blockDagStorage: BlockDagStorage[F]
  ): BlockStateManager[F] =
    BlockStateManagerImpl[F](blockStore, blockDagStorage)

  private final case class BlockStateManagerImpl[F[_]: Sync](
      blockStore: BlockStore[F],
      blockDagStorage: BlockDagStorage[F]
  ) extends BlockStateManager[F] {

    override def isEmpty: F[Boolean] =
      for {
        dag       <- blockDagStorage.getRepresentation
        firstHash = dag.topoSort(0, 1L.some)
      } yield firstHash.isEmpty
  }
}
