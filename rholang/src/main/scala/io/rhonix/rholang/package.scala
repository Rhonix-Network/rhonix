package io.rhonix

import io.rhonix.metrics.Metrics
import io.rhonix.rholang.interpreter.{RhoHistoryRepositorySyntax, RhoRuntimeSyntax}

package object rholang {
  val RholangMetricsSource: Metrics.Source = Metrics.Source(Metrics.BaseSource, "rholang")

  object syntax extends AllSyntaxRholang
}

trait AllSyntaxRholang extends RhoRuntimeSyntax with RhoHistoryRepositorySyntax
