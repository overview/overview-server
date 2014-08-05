package org.overviewproject.util

import org.slf4j.LoggerFactory
import org.slf4j.{Logger => JLogger}
import scala.math.ScalaNumber

trait Logger {
  protected val jLogger : JLogger

  /** Copied from Scala's StringLike.scala */
  private def unwrapArg(arg: Any): AnyRef = arg match {
    case x: ScalaNumber => x.underlying
    case x => x.asInstanceOf[AnyRef]
  }

  def trace(msg: String, args: Any*) = jLogger.trace(msg, args.map(unwrapArg): _*)
  def debug(msg: String, args: Any*) = jLogger.debug(msg, args.map(unwrapArg): _*)
  def info( msg: String, args: Any*) = jLogger.info( msg, args.map(unwrapArg): _*)
  def warn( msg: String, args: Any*) = jLogger.warn( msg, args.map(unwrapArg): _*)
  def error(msg: String, args: Any*) = jLogger.error(msg, args.map(unwrapArg): _*)

  def logElapsedTime(op: String, t0: Long, args: Any*): Unit = {
    val t1 = System.nanoTime()
    val ms = (t1 - t0) / 1000000
    info(s"${op}, time: {}ms", (args :+ ms): _*)
  }

  /** Runs the given block and then logs the time taken.
    *
    * The message will still be logged, even if the block throws an exception.
    */
  def logExecutionTime[T](op:String, args: Any*)(fn : => T) : T = {
    val t0 = System.nanoTime()

    try {
      fn
    } finally {
      logElapsedTime(op, t0, args: _*)
    }
  }
}

class SLogger(override protected val jLogger: JLogger) extends Logger

/** Logging interface, relying on Logback.
  *
  * There are places for you to call methods:
  *
  * `Logger.info("message {} {}", arg1, arg2)` will use a singleton object and
  * prefix messages with "WORKER".
  *
  * `val logger = Logger.forClass[MyClass]; logger.info("message {} {}", arg1, arg2`
  * will prefix messages with the fully-qualified name of MyClass.
  */
object Logger extends Logger {
  override protected lazy val jLogger = LoggerFactory.getLogger("WORKER")

  /** Use this rather than using static Logger */
  def forClass(clazz: Class[_]): Logger = new SLogger(LoggerFactory.getLogger(clazz))
}
