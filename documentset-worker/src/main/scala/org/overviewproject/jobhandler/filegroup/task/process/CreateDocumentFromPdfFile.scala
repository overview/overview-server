package org.overviewproject.jobhandler.filegroup.task.process

import akka.actor.ActorRef
import org.overviewproject.jobhandler.filegroup.task.step.TaskStep
import org.overviewproject.models.File


object CreateDocumentFromPdfFile extends UploadedFileProcessCreator {
  def create(documentSetId: Long, documentIdSupplier: ActorRef) = new UploadedFileProcess {
    override protected val steps =
      DoCreatePdfFile(documentSetId).andThen(
        DoExtractTextFromPdf(documentSetId).andThen(
          DoRequestDocumentIds(documentIdSupplier, documentSetId).andThen(
            DoWriteDocuments())))

  }
}