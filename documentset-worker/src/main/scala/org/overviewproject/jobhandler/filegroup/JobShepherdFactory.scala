package org.overviewproject.jobhandler.filegroup

import akka.actor.ActorRef

trait JobShepherdFactory {
  def createShepherd(documentSetId: Long, job: FileGroupJob, taskQueue: ActorRef, progressReporter: ActorRef): JobShepherd  
}

class FileGroupJobShepherdFactory extends JobShepherdFactory {

  override def createShepherd(documentSetId: Long, job: FileGroupJob, taskQueue: ActorRef, progressReporter: ActorRef): JobShepherd = job match {
    case CreateDocumentsJob(fileGroupId, splitDocuments) => 
      CreateDocumentsJobShepherd(documentSetId, fileGroupId, taskQueue, progressReporter)
    case DeleteFileGroupJob(fileGroupId) => 
      new DeleteFileGroupJobShepherd(documentSetId, fileGroupId, taskQueue)
  }
}
