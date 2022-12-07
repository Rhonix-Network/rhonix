package io.rhonix.node.benchmark.utils

import cats.syntax.all._
import io.rhonix.casper.genesis.Genesis
import io.rhonix.casper.genesis.contracts.{ProofOfStake, Registry, Validator, Vault}
import io.rhonix.crypto.PublicKey
import io.rhonix.rholang.interpreter.util.RevAddress

import scala.collection.Seq

object GenesisParams {

  val predefinedVaultsAmt = 900000000L

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
        posMultiSigPublicKeys = List(),
        posMultiSigQuorum = 1,
        posVaultPubKey = ""
      ),
      vaults = genesisVaults.map(predefinedVault) ++
        bondedValidators.toList.map {
          case Validator(pk, _) =>
            // Initial validator vaults contain 0 Rev
            RevAddress.fromPublicKey(pk).map(Vault(_, 0))
        }.flattenOption,
      blockNumber = 0,
      sender = genesisVaults.head,
      registry = Registry("")
    )
  }

}
