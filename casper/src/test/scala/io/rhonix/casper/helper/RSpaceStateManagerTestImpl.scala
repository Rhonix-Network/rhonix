package io.rhonix.casper.helper

import cats.syntax.all._
import cats.effect.Sync
import io.rhonix.rspace.state.{RSpaceExporter, RSpaceImporter, RSpaceStateManager}

final case class RSpaceStateManagerTestImpl[F[_]: Sync]() extends RSpaceStateManager[F] {
  override def exporter: RSpaceExporter[F] = ???

  override def importer: RSpaceImporter[F] = ???

  override def isEmpty: F[Boolean] = ???
}
