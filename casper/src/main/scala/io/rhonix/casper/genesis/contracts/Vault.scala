package io.rhonix.casper.genesis.contracts
import io.rhonix.rholang.interpreter.util.RevAddress

final case class Vault(revAddress: RevAddress, initialBalance: Long)
