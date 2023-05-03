package io.rhonix

import io.rhonix.comm.transport.TransportLayerSyntax
import io.rhonix.metrics.Metrics

package object comm {
  val CommMetricsSource: Metrics.Source = Metrics.Source(Metrics.BaseSource, "comm")

  // Importing syntax object means using all extensions in the project
  object syntax extends AllSyntaxComm
}

// Comm syntax
trait AllSyntaxComm extends TransportLayerSyntax
