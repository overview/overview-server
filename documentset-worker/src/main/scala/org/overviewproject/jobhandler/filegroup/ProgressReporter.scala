package org.overviewproject.jobhandler.filegroup

import akka.actor.Actor

object ProgressReporterProtocol {
  case class StartJob(documentSetId: Long, numberOfTasks: Int)

}

trait ProgressReporter extends Actor {
  import ProgressReporterProtocol._

  protected val storage: Storage 
  
  protected trait Storage {
    def updateProgress(documentSetId: Long, fraction: Double, description: String): Unit  
  }
  
  def receive = {
    case StartJob(documentSetId, numberOfTasks) => 
      storage.updateProgress(documentSetId, 0, description(0, numberOfTasks)) 
  }
  
  private def description(tasksStarted: Int, numberOfTasks: Int): String = 
    s"processing_files:$tasksStarted:$numberOfTasks"
}