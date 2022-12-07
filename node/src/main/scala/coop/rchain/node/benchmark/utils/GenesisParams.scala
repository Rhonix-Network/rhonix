package io.rhonix.node.benchmark.utils

import cats.syntax.all._
import io.rhonix.casper.genesis.Genesis
import io.rhonix.casper.genesis.contracts.{ProofOfStake, Registry, Validator, Vault}
import io.rhonix.crypto.PublicKey
import io.rhonix.rholang.interpreter.util.RevAddress

object GenesisParams {

  val predefinedVaultsAmt = 900000000L
  val posVK =
    "04eccad1d78ea16046f4787ffba9b36bec5ef151aba14fa46aeca8e14b6e604812d7d1deb50e0931fa0dbac63dbe0f86bf61c3c93a69c17071427e1580260cbb8e"

  def genesisParameters(
      bondedValidators: Seq[Validator],
      genesisVaults: List[PublicKey],
      epochLength: Int = 1000
  ): Genesis = {
    def predefinedVault(pub: PublicKey): Vault =
      Vault(RevAddress.fromPublicKey(pub).get, predefinedVaultsAmt)

    Genesis(
      shardId = "root",
      proofOfStake = ProofOfStake(
        minimumBond = 0L,
        maximumBond = Long.MaxValue,
        // Epoch length is set to large number to prevent trigger of epoch change
        // in PoS close block method, which causes block merge conflicts
        // - epoch change can be set as a parameter in Rholang tests (e.g. PoSSpec)
        epochLength = epochLength,
        quarantineLength = 50000,
        numberOfActiveValidators = 100,
        validators = bondedValidators,
        posMultiSigPublicKeys = List(posVK),
        posMultiSigQuorum = 1,
        posVaultPubKey = posVK
      ),
      vaults = genesisVaults.map(predefinedVault) ++
        bondedValidators.toList.map {
          case Validator(pk, _) =>
            // Initial validator vaults contain 0 Rev
            RevAddress.fromPublicKey(pk).map(Vault(_, 0))
        }.flattenOption,
      blockNumber = 0,
      sender = genesisVaults.head,
      registry = Registry(posVK)
    )
  }

}
