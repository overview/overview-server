package com.overviewdocs.jobhandler.filegroup

import akka.actor.ActorRef

trait JobShepherdFactory {
  def createShepherd(documentSetId: Long, job: CreateDocumentsJob,
      taskQueue: ActorRef, progressReporter: ActorRef, documentIdSupplier: ActorRef): CreateDocumentsJobShepherd
}

class FileGroupJobShepherdFactory extends JobShepherdFactory {

  override def createShepherd(
    documentSetId: Long,
    job: CreateDocumentsJob,
    taskQueue: ActorRef,
    progressReporter: ActorRef,
    documentIdSupplier: ActorRef
  ): CreateDocumentsJobShepherd = {
    CreateDocumentsJobShepherd(
      documentSetId,
      job.fileGroupId,
      job.options,
      taskQueue,
      progressReporter,
      documentIdSupplier
    )
  }
}
