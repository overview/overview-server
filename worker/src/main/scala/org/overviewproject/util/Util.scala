/**
 * Utils.scala
 *
 * Logging singleton, ActorSystem singleton, progress reporting classes
 *
 * Overview Project, created August 2012
 * @author Jonathan Stray
 *
 */

package org.overviewproject.util

import akka.actor._
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



// Singleton Akka actor system object. One per process, managing all actors.
object WorkerActorSystem {
  def withActorSystem(f: ActorSystem => Unit) {
    val context = ActorSystem("WorkerActorSystem")
    f(context)
    context.shutdown
  }
}

// Iterator that loops infinitely over an underlying (presumably finite) iterator
// Resets when it hits the end by calling makeIter. Can be empty if makeIter returns empty iter.
class LoopedIterator[T](makeIter: => Iterator[T]) extends Iterator[T] {
  
  private var current = makeIter
  private val trulyEmpty = current.isEmpty
  
  def hasNext = !trulyEmpty
  def next : T = {
    if (!current.hasNext && !trulyEmpty)
      current = makeIter
    current.next
  }  
}

/**
 * Enumeration for encoding job state descriptions into keys that can be internationalized by
 * the client. Description types can be defined to take a single argument, which is encoded
 * as "key:argument"
 */
sealed abstract class DocumentSetCreationJobStateDescription(val stateName: String, keys: String*) {
  
  override def toString = (stateName +: keys) mkString(":")
  
  def sameStateAs(that: DocumentSetCreationJobStateDescription): Boolean = stateName == that.stateName
}

object DocumentSetCreationJobStateDescription {
  private type Description = DocumentSetCreationJobStateDescription

  case object OutOfMemory extends Description("out_of_memory")
  case object WorkerError extends Description("worker_error")
  case class Retrieving(docNum: Int, total: Int) extends Description("retrieving_documents", docNum.toString, total.toString) 
  case object Clustering extends Description("clustering")
  case object Saving extends Description("saving_document_tree")
  case object Done extends Description("job_complete")
  case class ClusteringLevel(n: Int) extends Description("clustering_level", n.toString) 
  case class Parsing(parsed: Long, total: Long) extends Description("parsing_data", parsed.toString, total.toString) 
}

object Progress {
  import DocumentSetCreationJobStateDescription._
  // Little class that represents progress
  case class Progress(fraction: Double, status: DocumentSetCreationJobStateDescription, hasError: Boolean = false)

  // Callback function to inform of progress, and returns true if operation should abort
  type ProgressAbortFn = Progress => Boolean

  // Turns a sub-task progress into overall task progress
  def makeNestedProgress(inner: ProgressAbortFn, startFraction: Double, endFraction: Double): ProgressAbortFn = {
    (progress) => inner(Progress(startFraction + (endFraction - startFraction) * progress.fraction, progress.status, progress.hasError))
  }

  // stub that you can pass in when you don't case about progress reporting
  def NoProgressReporting(p: Progress): Boolean = { false }
}

