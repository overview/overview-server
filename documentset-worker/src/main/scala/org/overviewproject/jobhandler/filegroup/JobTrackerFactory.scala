package org.overviewproject.jobhandler.filegroup

import akka.actor.ActorRef

trait JobTrackerFactory {
  def createTracker(documentSetId: Long, job: FileGroupJob, taskQueue: ActorRef): JobTracker  
}

class FileGroupJobTrackerFactory extends JobTrackerFactory {

  override def createTracker(documentSetId: Long, job: FileGroupJob, taskQueue: ActorRef): JobTracker = job match {
    case CreateDocumentsJob(fileGroupId) => new CreateDocumentsJobTrackerImpl(documentSetId, fileGroupId, taskQueue)
    case DeleteFileGroupJob(fileGroupId) => println("0000");new DeleteFileGroupJobTracker(documentSetId, fileGroupId, taskQueue)
  }
}
