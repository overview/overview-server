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


object DocumentSetCreationJobStateDescription extends Enumeration {
  type DocumentSetCreationJobStateDescription = Value

  val NoDescription = Value(0, "")
  val OutOfMemory = Value(1, "out_of_memory")
  val WorkerError = Value(2, "worker_error")
  val Retrieving = Value(3, "retrieving_documents")
  val Clustering = Value(4, "clustering_documents")
  val ClusteringLevel1 = Value(5, "clustering_level_1")
  val ClusteringLevel2 = Value(6, "clustering_level_2")
  val ClusteringLevel3 = Value(7, "clustering_level_3")
  val ClusteringLevel4 = Value(8, "clustering_level_4")
  val ClusteringLevel5 = Value(9, "clustering_level_5")
  val ClusteringLevel6 = Value(10, "clustering_level_6")
  val ClusteringLevel7 = Value(11, "clustering_level_7")
  val ClusteringLevel8 = Value(12, "clustering_level_8")
  val ClusteringLevel9 = Value(13, "clustering_level_9")
  val ClusteringLevel10 = Value(14, "clustering_level_10")
  val ClusteringLevel11 = Value(15, "clustering_level_10")
  val Saving = Value(16, "saving_document_tree")
  val Done = Value(17, "job_complete")
}

object Progress {
  import DocumentSetCreationJobStateDescription._
  // Little class that represents progress
  case class Progress(fraction:Double, status:DocumentSetCreationJobStateDescription, hasError:Boolean = false) 

  // Callback function to inform of progress, and returns false if operation should abort
  type ProgressAbortFn = (Progress) => Boolean 
  
  // Turns a sub-task progress into overall task progress
  def makeNestedProgress(inner:ProgressAbortFn, startFraction:Double, endFraction:Double) : ProgressAbortFn = {
    (progress) => inner(Progress(startFraction + (endFraction - startFraction) * progress.fraction, progress.status, progress.hasError))
  }
  
  // stub that you can pass in when you don't case about progress reporting
  def NoProgressReporting(p:Progress):Boolean = { false } 
}

