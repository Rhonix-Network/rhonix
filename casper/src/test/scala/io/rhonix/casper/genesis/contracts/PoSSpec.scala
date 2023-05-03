package io.rhonix.casper.genesis.contracts

import io.rhonix.casper.helper.RhoSpec
import io.rhonix.casper.util.GenesisBuilder
import io.rhonix.crypto.PublicKey
import io.rhonix.models.NormalizerEnv
import io.rhonix.rholang.build.CompiledRholangSource
import io.rhonix.rholang.interpreter.util.RevAddress
import io.rhonix.models.syntax._
import scala.concurrent.duration._

class PosSpec
    extends RhoSpec(
      CompiledRholangSource("PosTest.rho", NormalizerEnv.Empty),
      Seq.empty,
      400.seconds,
      genesisParameters = {
        val p = GenesisBuilder.buildGenesisParametersSize(4)
        (p._1, p._2, p._3.copy(vaults = p._3.vaults ++ PosSpec.testVaults))
      }
    )

object PosSpec {

  def prepareVault(vaultData: (String, Long)): Vault =
    Vault(RevAddress.fromPublicKey(PublicKey(vaultData._1.unsafeDecodeHex)).get, vaultData._2)

  val testVaults: Seq[Vault] = Seq(
    ("0" * 130, 10000L),
    ("1" * 130, 10000L),
    ("2" * 130, 10000L),
    ("3" * 130, 10000L),
    ("4" * 130, 10000L),
    ("5" * 130, 10000L),
    ("6" * 130, 10000L),
    ("7" * 130, 10000L),
    ("8" * 130, 10000L),
    ("9" * 130, 10000L),
    ("a" * 130, 10000L),
    ("b" * 130, 10000L),
    ("c" * 130, 10000L),
    ("d" * 130, 10000L),
    ("e" * 130, 10000L)
  ).map(prepareVault)
}
