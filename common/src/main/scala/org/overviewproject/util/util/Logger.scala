package org.overviewproject.util

import org.slf4j.LoggerFactory


// Worker logging singleton object. Pass-through to LogBack
object Logger {
  private def logger = LoggerFactory.getLogger("WORKER")

  def trace(msg: String) = logger.trace(msg)
  def debug(msg: String) = logger.debug(msg)
  def info(msg: String) = logger.info(msg)
  def warn(msg: String) = logger.warn(msg)
  def error(msg: String) = logger.error(msg)

  def trace(msg: String, t: Throwable) = logger.trace(msg, t)
  def debug(msg: String, t: Throwable) = logger.debug(msg, t)
  def info(msg: String, t: Throwable) = logger.info(msg, t)
  def warn(msg: String, t: Throwable) = logger.warn(msg, t)
  def error(msg: String, t: Throwable) = logger.error(msg, t)

  def logElapsedTime(op: String, t0: Long) {
    val t1 = System.nanoTime()
    info(op + ", time: " + ("%.3f" format (t1 - t0) / 1e6) + "ms")
  }

  /** Runs the given block and then logs the time taken.
    *
    * The message will still be logged, even if the block throws an exception.
    */
  def logExecutionTime[T](op:String, logIt:Boolean = true)(fn : => T) : T = {
    val t0 = System.nanoTime()

    try {
      fn
    } finally {
      if (logIt) {
        logElapsedTime(op, t0)
      }
    }
  }
}
