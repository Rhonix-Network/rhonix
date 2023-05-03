package io.rhonix.casper.genesis.contracts
import io.rhonix.casper.helper.RhoSpec
import io.rhonix.models.NormalizerEnv
import io.rhonix.rholang.build.CompiledRholangSource

class EitherSpec
    extends RhoSpec(
      CompiledRholangSource("EitherTest.rho", NormalizerEnv.Empty),
      Seq.empty,
      GENESIS_TEST_TIMEOUT
    )
