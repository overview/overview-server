package org.overviewproject.jobhandler.filegroup.task.process

import org.overviewproject.jobhandler.filegroup.task.step.TaskStep
import akka.actor.ActorRef

trait CreateDocumentFromPdfPage {
  def start(uploadedFileId: Long): TaskStep
}

object CreateDocumentFromPdfPage {
  def apply(documentSetId: Long, documentIdSupplier: ActorRef) = new CreateDocumentFromPdfPage {
    private val steps =
      DoCreatePdfFile(documentSetId).andThen(
        DoCreatePdfPages().andThen(
          DoRequestDocumentIds(documentIdSupplier, documentSetId).andThen(
            DoWriteDocuments())))

    override def start(uploadedFileId: Long) = steps.generate(uploadedFileId)
  }
}