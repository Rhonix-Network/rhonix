package io.rhonix.casper.helper
import cats.effect.Concurrent
import io.rhonix.metrics.Span
import io.rhonix.models.ListParWithRandom
import io.rhonix.models.rholang.RhoType
import io.rhonix.rholang.interpreter.SystemProcesses.ProcessContext
import io.rhonix.rholang.interpreter.{ContractCall, PrettyPrinter}
import io.rhonix.shared.{Log, LogSource}

object RhoLoggerContract {
  val prettyPrinter = PrettyPrinter()

  //TODO extract a `RhoPatterns[F]` algebra that will move passing the Span, the Dispatcher, and the Space parameters closer to the edge of the world
  def handleMessage[F[_]: Log: Concurrent: Span](
      ctx: ProcessContext[F]
  )(message: Seq[ListParWithRandom]): F[Unit] = {
    val isContractCall = new ContractCall(ctx.space, ctx.dispatcher)

    message match {
      case isContractCall(_, Seq(RhoType.RhoString(logLevel), par)) =>
        val msg         = prettyPrinter.buildString(par)
        implicit val ev = LogSource.matLogSource

        logLevel match {
          case "trace" => Log[F].trace(msg)
          case "debug" => Log[F].debug(msg)
          case "info"  => Log[F].info(msg)
          case "warn"  => Log[F].warn(msg)
          case "error" => Log[F].error(msg)
        }
    }
  }
}
