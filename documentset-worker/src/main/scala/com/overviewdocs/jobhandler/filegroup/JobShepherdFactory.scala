package com.overviewdocs.jobhandler.filegroup

import akka.actor.ActorRef

trait JobShepherdFactory {
  def createShepherd(documentSetId: Long, job: CreateDocumentsJob,
      taskQueue: ActorRef, progressReporter: ActorRef, documentIdSupplier: ActorRef): JobShepherd
}

class FileGroupJobShepherdFactory extends JobShepherdFactory {

  override def createShepherd(documentSetId: Long, job: CreateDocumentsJob,
      taskQueue: ActorRef, progressReporter: ActorRef, documentIdSupplier: ActorRef): JobShepherd =
    job match {
      case CreateDocumentsJob(fileGroupId, options) =>
        CreateDocumentsJobShepherd(documentSetId, fileGroupId, options, 
            taskQueue, progressReporter, documentIdSupplier)
    }
}
