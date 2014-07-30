package org.overviewproject.jobhandler.filegroup

import akka.actor.ActorRef

trait JobTrackerFactory {
  def createTracker(documentSetId: Long, job: FileGroupJob, taskQueue: ActorRef, progressReporter: ActorRef): JobTracker  
}

class FileGroupJobTrackerFactory extends JobTrackerFactory {

  override def createTracker(documentSetId: Long, job: FileGroupJob, taskQueue: ActorRef, progressReporter: ActorRef): JobTracker = job match {
    case CreateDocumentsJob(fileGroupId) => 
      CreateDocumentsJobTracker(documentSetId, fileGroupId, taskQueue, progressReporter)
    case DeleteFileGroupJob(fileGroupId) => 
      new DeleteFileGroupJobTracker(documentSetId, fileGroupId, taskQueue)
  }
}
