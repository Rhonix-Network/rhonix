package io.rhonix.rspace.state.instances

import cats.effect.Sync
import cats.effect.concurrent.Ref
import cats.syntax.all._
import io.rhonix.catscontrib.Catscontrib._
import io.rhonix.catscontrib.ski._
import io.rhonix.rspace.state.instances.RSpaceExporterStore.NoRootError
import io.rhonix.rspace.state.{RSpaceExporter, RSpaceImporter, RSpaceStateManager}

object RSpaceStateManagerImpl {
  def apply[F[_]: Sync](
      exporter: RSpaceExporter[F],
      importer: RSpaceImporter[F]
  ): RSpaceStateManager[F] =
    RSpaceStateManagerImpl[F](exporter, importer)

  private final case class RSpaceStateManagerImpl[F[_]: Sync](
      exporter: RSpaceExporter[F],
      importer: RSpaceImporter[F]
  ) extends RSpaceStateManager[F] {

    override def isEmpty: F[Boolean] = hasRoot

    def hasRoot: F[Boolean] =
      exporter.getRoot.map(kp(true)).handleError {
        case NoRootError => false
      }
  }
}
