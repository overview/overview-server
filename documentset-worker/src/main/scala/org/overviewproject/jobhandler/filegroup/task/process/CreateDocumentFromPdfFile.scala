package org.overviewproject.jobhandler.filegroup.task.process

import akka.actor.ActorRef
import org.overviewproject.jobhandler.filegroup.task.step.TaskStep
import org.overviewproject.models.File


object CreateDocumentFromPdfFile {
  def apply(documentSetId: Long, filename: String, documentIdSupplier: ActorRef) = new UploadedFileProcess {
    override protected val steps =
      DoCreatePdfFile(documentSetId, filename: String).andThen(
        DoExtractTextFromPdf(documentSetId).andThen(
          DoRequestDocumentIds(documentIdSupplier, documentSetId, filename).andThen(
            DoWriteDocuments(documentSetId, filename))))

  }
}