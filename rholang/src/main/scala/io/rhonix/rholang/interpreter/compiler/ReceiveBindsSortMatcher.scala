package io.rhonix.rholang.interpreter.compiler

import cats.effect.Sync
import io.rhonix.models.rholang.sorter.ReceiveSortMatcher.sortBind
import io.rhonix.models.{Par, ReceiveBind, Var}
import cats.syntax.all._
import io.rhonix.models.rholang.sorter._
import io.rhonix.models.rholang.implicits._

object ReceiveBindsSortMatcher {
  // Used during normalize to presort the binds.
  def preSortBinds[F[_]: Sync, T](
      binds: Seq[(Seq[Par], Option[Var], Par, FreeMap[T])]
  ): F[Seq[(ReceiveBind, FreeMap[T])]] = {
    val bindSortings = binds.toList
      .map {
        case (
            patterns: Seq[Par],
            remainder: Option[Var],
            channel: Par,
            knownFree: FreeMap[T]
            ) =>
          for {
            sortedBind <- sortBind(
                           ReceiveBind(
                             patterns,
                             channel,
                             remainder,
                             freeCount = knownFree.countNoWildcards
                           )
                         )
          } yield ScoredTerm((sortedBind.term, knownFree), sortedBind.score)
      }

    for {
      binds <- bindSortings.sequence
    } yield binds.sorted.map(_.term)
  }

}
