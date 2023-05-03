package io.rhonix.node

import io.rhonix.node.configuration._
import io.rhonix.node.effects._
import io.rhonix.node.runtime.NodeMain
import io.rhonix.shared._
import monix.eval.Task
import monix.execution.Scheduler
import org.slf4j.LoggerFactory

object Main {

  /**
    * Main entry point
    * @param args input args
    */
  @SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
  def main(args: Array[String]): Unit = {
    // Catch-all for unhandled exceptions. Use only JDK and SLF4J.
    Thread.setDefaultUncaughtExceptionHandler((thread, ex) => {
      LoggerFactory.getLogger(getClass).error("Unhandled exception in thread " + thread.getName, ex)
    })

    // Main scheduler for all CPU bounded tasks
    // Should always be passed as implicit dependency.
    // All other schedulers should be explicit.
    implicit val scheduler: Scheduler = Scheduler.computation(
      Math.max(java.lang.Runtime.getRuntime.availableProcessors, 2),
      "node-runner",
      reporter = UncaughtExceptionLogger
    )
    implicit val console: ConsoleIO[Task] = NodeMain.consoleIO
    implicit val log: Log[Task]           = effects.log

    // Ensure terminal is restored on exit
    sys.addShutdownHook {
      console.close.runSyncUnsafe()
    }

    // Parse CLI options
    val options = commandline.Options(args)
    if (options.subcommand.contains(options.run))
      // Start the node
      NodeMain.startNode[Task](options).runSyncUnsafe()
    //or
    else
      // Execute CLI command
      NodeMain.runCLI[Task](options).runSyncUnsafe()
  }
}
