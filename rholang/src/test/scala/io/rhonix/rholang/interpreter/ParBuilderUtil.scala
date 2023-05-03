package io.rhonix.rholang.interpreter

import io.rhonix.models.Par
import io.rhonix.rholang.interpreter.compiler.Compiler
import monix.eval.Coeval
import org.scalatest.EitherValues._
import org.scalatest.Assertion
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper

object ParBuilderUtil {

  def mkTerm(rho: String): Either[Throwable, Par] =
    Compiler[Coeval].sourceToADT(rho, Map.empty[String, Par]).runAttempt

  def assertCompiledEqual(s: String, t: String): Assertion =
    ParBuilderUtil.mkTerm(s).value shouldBe ParBuilderUtil.mkTerm(t).value

}
