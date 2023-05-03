package io.rhonix

import io.rhonix.blockstorage.dag.{BlockDagStorageSyntax, DagRepresentationSyntax, MessageMapSyntax}
import io.rhonix.blockstorage.{ApprovedStoreSyntax, BlockStoreSyntax, ByteStringKVStoreSyntax}
import io.rhonix.metrics.Metrics

package object blockstorage {
  val BlockStorageMetricsSource: Metrics.Source =
    Metrics.Source(Metrics.BaseSource, "block-storage")

  // Importing syntax object means using all extensions in the project
  object syntax extends AllSyntaxBlockStorage
}

// Block storage syntax
trait AllSyntaxBlockStorage
    extends ApprovedStoreSyntax
    with BlockStoreSyntax
    with DagRepresentationSyntax
    with BlockDagStorageSyntax
    with ByteStringKVStoreSyntax
    with MessageMapSyntax
