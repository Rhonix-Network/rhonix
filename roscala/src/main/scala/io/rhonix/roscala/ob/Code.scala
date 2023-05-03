package io.rhonix.roscala.ob

import io.rhonix.roscala.Opcode

case class Code(litvec: Seq[Ob], codevec: Seq[Opcode]) extends Ob
