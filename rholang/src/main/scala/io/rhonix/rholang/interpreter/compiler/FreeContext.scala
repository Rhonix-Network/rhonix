package io.rhonix.rholang.interpreter.compiler

final case class FreeContext[T](level: Int, typ: T, sourcePosition: SourcePosition)
