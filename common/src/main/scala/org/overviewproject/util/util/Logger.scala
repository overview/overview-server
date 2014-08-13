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
  /*
   * We create loggers in "synchronized" blocks so that we don't create two at
   * once. If we did, SLF4J would output a cryptic error message and ignore
   * everything we tried to log.
   *
   * This bug has been around for four years.
   *
   * http://bugzilla.slf4j.org/show_bug.cgi?id=176
   */

  /** Used internally for a static Logger interface. */
  override protected lazy val jLogger = synchronized { LoggerFactory.getLogger("WORKER") }

  /** Use this to create a more useful Logger. */
  def forClass(clazz: Class[_]): Logger = synchronized { new SLogger(LoggerFactory.getLogger(clazz)) }
}
