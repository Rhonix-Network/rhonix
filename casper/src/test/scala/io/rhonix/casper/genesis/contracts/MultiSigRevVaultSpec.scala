package io.rhonix.casper.genesis.contracts

import io.rhonix.casper.helper.RhoSpec
import io.rhonix.casper.util.ConstructDeploy
import io.rhonix.models.NormalizerEnv
import io.rhonix.rholang.build.CompiledRholangSource

class MultiSigRevVaultSpec
    extends RhoSpec(
      CompiledRholangSource("MultiSigRevVaultTest.rho", NormalizerEnv.Empty),
      Seq.empty,
      GENESIS_TEST_TIMEOUT
    )
