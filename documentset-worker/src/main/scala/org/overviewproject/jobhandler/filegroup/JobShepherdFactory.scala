package com.overviewdocs.jobhandler.filegroup

import akka.actor.ActorRef

trait JobShepherdFactory {
  def createShepherd(documentSetId: Long, job: FileGroupJob,
      taskQueue: ActorRef, progressReporter: ActorRef, documentIdSupplier: ActorRef): JobShepherd
}

class FileGroupJobShepherdFactory extends JobShepherdFactory {

  override def createShepherd(documentSetId: Long, job: FileGroupJob,
      taskQueue: ActorRef, progressReporter: ActorRef, documentIdSupplier: ActorRef): JobShepherd =
    job match {
      case CreateDocumentsJob(fileGroupId, options) =>
        CreateDocumentsJobShepherd(documentSetId, fileGroupId, options, 
            taskQueue, progressReporter, documentIdSupplier)
      case DeleteFileGroupJob(fileGroupId) =>
        new DeleteFileGroupJobShepherd(documentSetId, fileGroupId, taskQueue)
    }
}
