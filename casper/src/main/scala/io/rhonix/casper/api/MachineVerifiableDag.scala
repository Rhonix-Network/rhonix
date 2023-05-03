package io.rhonix.casper.api

import io.rhonix.casper._
import io.rhonix.models.BlockHash.BlockHash
import io.rhonix.models.syntax._
import cats._
import cats.syntax.all._

final case class VerifiableEdge(from: String, to: String)

object VerifiableEdge {
  implicit def showVerifiableEdge: Show[VerifiableEdge] = new Show[VerifiableEdge] {
    def show(ve: VerifiableEdge): String = s"${ve.from} ${ve.to}"
  }
}

object MachineVerifiableDag {
  def apply[F[_]: Monad](
      toposort: TopoSort,
      fetchParents: BlockHash => F[List[BlockHash]]
  ): F[List[VerifiableEdge]] = {
    import VerifiableEdge._

    toposort
      .foldM(List.empty[VerifiableEdge]) {
        case (acc, blockHashes) =>
          blockHashes.toList
            .traverse { blockHash =>
              fetchParents(blockHash).map(parents => (blockHash.show, parents.map(p => p.show)))
            }
            .map { blocksAndParents =>
              blocksAndParents.flatMap {
                case (b, bp) => bp.map(VerifiableEdge(b, _))
              }
            }
            .map(_ ++ acc)

      }
  }
}
