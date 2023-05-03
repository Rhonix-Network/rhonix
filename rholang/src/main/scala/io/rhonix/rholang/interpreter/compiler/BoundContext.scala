package io.rhonix.rholang.interpreter.compiler

final case class BoundContext[T](index: Int, typ: T, sourcePosition: SourcePosition)
