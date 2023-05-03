package io.rhonix.casper.genesis.contracts
import io.rhonix.casper.helper.RhoSpec
import io.rhonix.models.NormalizerEnv
import io.rhonix.rholang.build.CompiledRholangSource

import scala.concurrent.duration._

class BlockDataContractSpec
    extends RhoSpec(
      CompiledRholangSource("BlockDataContractTest.rho", NormalizerEnv.Empty),
      Seq.empty,
      30.seconds
    )
