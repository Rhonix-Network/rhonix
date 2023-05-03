package io.rhonix.node.api

import cats.effect.Sync
import cats.syntax.all._
import io.rhonix.crypto.hash.Blake2b512Random
import io.rhonix.models.Par
import io.rhonix.monix.Monixable
import io.rhonix.node.model.repl._
import io.rhonix.rholang.interpreter.Interpreter._
import io.rhonix.rholang.interpreter.accounting.Cost
import io.rhonix.rholang.interpreter.compiler.Compiler
import io.rhonix.rholang.interpreter.errors.InterpreterError
import io.rhonix.rholang.interpreter.storage.StoragePrinter
import io.rhonix.rholang.interpreter.{RhoRuntime, _}
import io.rhonix.shared.syntax._
import monix.eval.Task
import monix.execution.Scheduler

object ReplGrpcService {

  def apply[F[_]: Monixable: Sync](runtime: RhoRuntime[F], worker: Scheduler): ReplGrpcMonix.Repl =
    new ReplGrpcMonix.Repl {
      def exec(source: String, printUnmatchedSendsOnly: Boolean = false): F[ReplResponse] =
        Sync[F]
          .attempt(
            Compiler[F]
              .sourceToADT(source, Map.empty[String, Par])
          )
          .flatMap {
            case Left(er) =>
              er match {
                case _: InterpreterError => Sync[F].delay(s"Error: ${er.toString}")
                case th: Throwable       => Sync[F].delay(s"Error: $th")
              }
            case Right(term) =>
              for {
                _ <- Sync[F].delay(printNormalizedTerm(term))
                res <- {
                  val rand = Blake2b512Random.defaultRandom
                  runtime.evaluate(source, Cost.UNSAFE_MAX, Map.empty[String, Par], rand)
                }
                prettyStorage <- if (printUnmatchedSendsOnly)
                                  StoragePrinter.prettyPrintUnmatchedSends(runtime)
                                else StoragePrinter.prettyPrint(runtime)
                EvaluateResult(cost, errors, _) = res
              } yield {
                val errorStr =
                  if (errors.isEmpty)
                    ""
                  else
                    errors
                      .map(_.toString())
                      .mkString("Errors received during evaluation:\n", "\n", "\n")
                s"Deployment cost: $cost\n" +
                  s"${errorStr}Storage Contents:\n$prettyStorage"
              }
          }
          .map(ReplResponse(_))

      private def defer[A](task: F[A]): Task[A] =
        task.toTask.executeOn(worker)

      def run(request: CmdRequest): Task[ReplResponse] =
        defer(exec(request.line))

      def eval(request: EvalRequest): Task[ReplResponse] =
        defer(exec(request.program, request.printUnmatchedSendsOnly))

      private def printNormalizedTerm(normalizedTerm: Par): Unit = {
        Console.println("\nEvaluating:")
        Console.println(PrettyPrinter().buildString(normalizedTerm))
      }
    }
}
