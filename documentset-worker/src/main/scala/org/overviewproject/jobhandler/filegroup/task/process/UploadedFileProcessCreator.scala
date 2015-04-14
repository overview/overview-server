package org.overviewproject.jobhandler.filegroup.task.process

import akka.actor.ActorRef

trait UploadedFileProcessCreator {
  def create(documentSetId: Long, documentIdSupplier: ActorRef): UploadedFileProcess
}