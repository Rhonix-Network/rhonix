package io.rhonix.casper.genesis.contracts

import io.rhonix.casper.helper.RhoSpec
import io.rhonix.casper.util.ConstructDeploy
import io.rhonix.models.NormalizerEnv
import io.rhonix.rholang.build.CompiledRholangSource
import io.rhonix.models.rholang.implicits._

class RevVaultSpec
    extends RhoSpec(
      CompiledRholangSource("RevVaultTest.rho", RevVaultSpec.normalizerEnv),
      Seq.empty,
      GENESIS_TEST_TIMEOUT
    )

object RevVaultSpec {
  val deployerPk    = ConstructDeploy.defaultPub
  val normalizerEnv = NormalizerEnv.withDeployerId(deployerPk)
}
