package io.rhonix.rholang.interpreter

import io.rhonix.rholang.interpreter.compiler.Compiler
import io.rhonix.models.Par
import io.rhonix.rholang.interpreter.errors.LexerError
import monix.eval.Coeval
import org.scalatest.EitherValues._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class LexerTest extends AnyFlatSpec with Matchers {

  def attemptMkTerm(input: String): Either[Throwable, Par] =
    Compiler[Coeval].sourceToADT(input).runAttempt()

  "Lexer" should "return LexerError for unterminated string at EOF" in {
    val attempt = attemptMkTerm("""{{ @"ack!(0) }}""")
    attempt.left.value shouldBe a[LexerError]
  }

  it should "return LexerError for unterminated string on particular line" in {
    val attempt = attemptMkTerm("{{ @\"ack\n!(0) }}")
    attempt.left.value shouldBe a[LexerError]
  }

  it should "return LexerError for illegal character" in {
    val attempt =
      attemptMkTerm("""("x is ${value}" ^ {"value" : x})""")
    attempt.left.value shouldBe a[LexerError]
  }
}
