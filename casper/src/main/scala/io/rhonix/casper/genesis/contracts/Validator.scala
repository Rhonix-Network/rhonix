package io.rhonix.casper.genesis.contracts

import io.rhonix.crypto.PublicKey

final case class Validator(pk: PublicKey, stake: Long)
