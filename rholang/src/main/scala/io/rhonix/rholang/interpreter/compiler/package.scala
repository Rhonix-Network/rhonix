package io.rhonix.rholang.interpreter

package object compiler {

  type IdContext[T] = (String, T, SourcePosition)

}
