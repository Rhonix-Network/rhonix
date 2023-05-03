package io.rhonix

import io.rhonix.casper.blocks.BlockDagStorageSyntax
import io.rhonix.casper.blocks.proposer.ProposerResult
import io.rhonix.casper.protocol.CommUtilSyntax
import io.rhonix.casper.rholang.syntax.{RuntimeManagerSyntax, RuntimeReplaySyntax, RuntimeSyntax}
import io.rhonix.metrics.Metrics
import io.rhonix.models.BlockHash.BlockHash

package object casper {
  type TopoSort             = Vector[Vector[BlockHash]]
  type BlockProcessing[A]   = Either[InvalidBlock, A]
  type ValidBlockProcessing = BlockProcessing[ValidBlock]

  type ProposeFunction[F[_]] = Boolean => F[ProposerResult]

  val CasperMetricsSource: Metrics.Source = Metrics.Source(Metrics.BaseSource, "casper")

  val MergingMetricsSource: Metrics.Source = Metrics.Source(CasperMetricsSource, "merging")

  // Importing syntax object means using all extensions in the project
  object syntax extends AllSyntaxCasper with AllSyntaxComm with AllSyntaxBlockStorage
}

// Casper syntax
trait AllSyntaxCasper
    extends CommUtilSyntax
    with BlockDagStorageSyntax
    with RuntimeManagerSyntax
    with RuntimeSyntax
    with RuntimeReplaySyntax
