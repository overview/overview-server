package org.overviewproject.jobhandler.filegroup.task.process

import akka.actor.ActorRef
import org.overviewproject.jobhandler.filegroup.task.step.TaskStep

object CreateDocumentFromPdfPage {
  def apply(documentSetId: Long, filename: String, documentIdSupplier: ActorRef) = new UploadedFileProcess {
    override protected val steps =
      DoCreatePdfFile(documentSetId, filename).andThen(
        DoCreatePdfPages(documentSetId).andThen(
          DoRequestDocumentIds(documentIdSupplier, documentSetId, filename).andThen(
            DoWriteDocuments(documentSetId, filename))))

  }
}