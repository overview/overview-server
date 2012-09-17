/**
 * Utils.scala
 * 
 * Logging singleton, ActorSystem singleton, progress reporting classes
 * 
 * Overview Project, created August 2012
 * @author Jonathan Stray
 * 
 */

package overview.util

import akka.actor._
import org.slf4j.LoggerFactory

// Worker logging singleton object. Pass-through to LogBack
object Logger {
   private def logger = LoggerFactory.getLogger("WORKER")

   def trace(msg:String) = logger.trace(msg)
   def debug(msg:String) = logger.debug(msg)
   def info(msg:String) = logger.info(msg)
   def warn(msg:String) = logger.warn(msg)
   def error(msg:String) = logger.error(msg)   
}

// Singleton Akka actor system object. One per process, managing all actors.
object WorkerActorSystem {
  def withActorSystem(f: ActorSystem => Unit) {
    val context = ActorSystem("WorkerActorSystem")
    f(context)
    context.shutdown
  }
}

object Progress {

  // Little class that represents progress
  case class Progress(fraction:Double, status:String, hasError:Boolean = false) 

  // Callback function to inform of progress, and returns false if operation should abort
  type ProgressAbortFn = (Progress) => Boolean 
  
  // Turns a sub-task progress into overall task progress
  def makeNestedProgress(inner:ProgressAbortFn, startFraction:Double, endFraction:Double) : ProgressAbortFn = {
    (progress) => inner(Progress(startFraction + (endFraction - startFraction) * progress.fraction, progress.status, progress.hasError))
  }
  
  // stub that you can pass in when you don't case about progress reporting
  def NoProgressReporting(p:Progress):Boolean = { false } 
}

