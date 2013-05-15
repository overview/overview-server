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
  
  def logElapsedTime(op: String, t0: Long) {
    val t1 = System.nanoTime()
    info(op + ", time: " + ("%.3f" format (t1 - t0) / 1e9) + " seconds")
  }

  // Version of the above that can be used as a custom control structure. Takes a string and a code block
  // optional boolean can be used for conditional logging
  def logExecutionTime(op:String, logIt:Boolean = true)(fn : => Unit) : Unit = {
    val t0 = System.nanoTime()
    fn
    if (logIt)
      logElapsedTime(op, t0)
  }
}
