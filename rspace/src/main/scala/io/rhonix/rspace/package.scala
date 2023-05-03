package io.rhonix

import io.rhonix.metrics.Metrics
import io.rhonix.rspace.history.syntax.HistoryReaderSyntax
import io.rhonix.rspace.state.RSpaceExporterSyntax
import io.rhonix.rspace.store.RSpaceStoreManagerSyntax

package object rspace {
  val RSpaceMetricsSource: Metrics.Source = Metrics.Source(Metrics.BaseSource, "rspace")

  // Importing syntax object means using all extensions in the project
  object syntax extends AllSyntaxRSpace
}
trait AllSyntaxRSpace
    extends RSpaceExporterSyntax
    with HistoryReaderSyntax
    with RSpaceStoreManagerSyntax
