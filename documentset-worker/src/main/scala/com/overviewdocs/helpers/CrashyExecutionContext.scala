package com.overviewdocs.helpers

import java.util.concurrent.ForkJoinPool
import scala.concurrent.{ExecutionContext,ExecutionContextExecutorService}

/** An ExecutionContext that will bring down the entire VM on uncaught
  * exception.
  *
  * The most important uncaught exception for us: OutOfMemoryError. We really
  * don't want to hang when that exception occurs.
  */
object CrashyExecutionContext {
  def apply(): ExecutionContextExecutorService = {
    val pool = new ForkJoinPool(
      1,
      ForkJoinPool.defaultForkJoinWorkerThreadFactory,
      new Thread.UncaughtExceptionHandler {
        override def uncaughtException(t: Thread, ex: Throwable): Unit = {
          ex.printStackTrace()
          Runtime.getRuntime.halt(1)
        }
      },
      false
    )

    ExecutionContext.fromExecutorService(pool)
  }
}
