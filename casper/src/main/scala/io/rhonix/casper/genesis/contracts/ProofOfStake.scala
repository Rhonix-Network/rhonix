package rhonix.casper.genesis.contracts

import io.rhonix.models.NormalizerEnv
import io.rhonix.rholang.build.CompiledRholangTemplate
import io.rhonix.shared.Base16

// TODO: Eliminate validators argument if unnecessary.
// TODO: eliminate the default for epochLength. Now it is used in order to minimise the impact of adding this parameter
// TODO: Remove hardcoded keys from standard deploys: https://rchain.atlassian.net/browse/RHONIX-3321?atlOrigin=eyJpIjoiNDc0NjE4YzYxOTRkNDcyYjljZDdlOWMxYjE1NWUxNjIiLCJwIjoiaiJ9
final case class ProofOfStake(
    minimumBond: Long,
    maximumBond: Long,
    validators: Seq[Validator],
    epochLength: Int,
    quarantineLength: Int,
    numberOfActiveValidators: Int,
    posMultiSigPublicKeys: List[String],
    posMultiSigQuorum: Int,
    posVaultPubKey: String
) extends CompiledRholangTemplate(
      "Pos.rhox",
      NormalizerEnv.Empty,
      "minimumBond"              -> minimumBond,
      "maximumBond"              -> maximumBond,
      "initialBonds"             -> ProofOfStake.initialBonds(validators),
      "epochLength"              -> epochLength,
      "quarantineLength"         -> quarantineLength,
      "numberOfActiveValidators" -> numberOfActiveValidators,
      "posMultiSigPublicKeys"    -> ProofOfStake.publicKeys(posMultiSigPublicKeys),
      "posMultiSigQuorum"        -> posMultiSigQuorum,
      "posVaultPubKey"           -> posVaultPubKey
    ) {

  require(minimumBond <= maximumBond)

  require(validators.nonEmpty)

}

object ProofOfStake {
  // TODO: Determine how the "initial bonds" map can simulate transferring stake into the PoS contract
  //       when this must be done during genesis, under the authority of the genesisPk, which calls the
  //       linear receive in Pos.rho
  def initialBonds(validators: Seq[Validator]): String = {
    import io.rhonix.crypto.util.Sorting.publicKeyOrdering
    val sortedValidators = validators.sortBy(_.pk)
    val mapEntries = sortedValidators.iterator
      .map { validator =>
        val pkString = Base16.encode(validator.pk.bytes)
        s""" "$pkString".hexToBytes() : ${validator.stake}"""
      }
      .mkString(", ")
    s"{$mapEntries}"
  }

  def publicKeys(posMultiSigPublicKeys: List[String]): String = {
    val indentBrackets = 12
    val indentKeys     = indentBrackets + 2
    val pubKeyItems = posMultiSigPublicKeys
      .map(pk => s"""${" " * indentKeys}"$pk".hexToBytes()""")
      .mkString(",\n")
    s"""[\n$pubKeyItems\n${" " * indentBrackets}]"""
  }
}
