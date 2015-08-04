package com.overviewdocs.util

import org.slf4j.LoggerFactory
import org.slf4j.{Logger => JLogger}
import scala.math.ScalaNumber
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class Logger(private val jLogger: JLogger) {
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

  def logExecutionTimeAsync[T](op: String, args: Any*)(fn: => Future[T]): Future[T] = {
    val t0 = System.nanoTime()

    fn
      .transform(
        { x => logElapsedTime(op, t0, args: _*); x },
        { x => logElapsedTime(op, t0, args: _*); x }
      )
  }
}

/** Logging interface, relying on Logback.
  *
  * Call it like this:
  *
  *   class Foo {
  *     val logger = Logger.forClass(getClass)
  *     def doSomething: Unit = {
  *       logger.info("Doing something")
  *     }
  *   }
  *
  * All messages will be prefixed with the class name. Use Logger configuration
  * to determine what to do with the log messages.
  */
object Logger {
  /*
   * We create loggers in "synchronized" blocks so that we don't create two at
   * once. If we did, SLF4J would output a cryptic error message and ignore
   * everything we tried to log.
   *
   * This is an ancient, silly bug: http://jira.qos.ch/browse/SLF4J-167
   */

  /** Use this to create a more useful Logger. */
  def forClass(clazz: Class[_]): Logger = synchronized { new Logger(LoggerFactory.getLogger(clazz)) }

  def forClassName(name: String): Logger = synchronized { new Logger(LoggerFactory.getLogger(name)) }
}
