package io.rhonix.casper.genesis.contracts

import io.rhonix.models.NormalizerEnv
import io.rhonix.rholang.build.CompiledRholangTemplate
import io.rhonix.shared.Base16

final case class Registry(systemContractPubKey: String)
    extends CompiledRholangTemplate(
      "Registry.rho",
      NormalizerEnv.Empty,
      "systemContractPubKey" -> systemContractPubKey
    )
