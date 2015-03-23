package org.overviewproject.jobhandler.filegroup.task.process

import akka.actor.ActorRef
import org.overviewproject.models.File
import org.overviewproject.jobhandler.filegroup.task.step.TaskStep

trait CreateDocumentFromPdfFile {
  def start(uploadedFileId: Long): TaskStep
}

object CreateDocumentFromPdfFile {
  def apply(documentSetId: Long, documentIdSupplier: ActorRef) = new CreateDocumentFromPdfFile {
    private val steps =
      DoCreatePdfFile(documentSetId).andThen(
        DoExtractTextFromPdf(documentSetId).andThen(
          DoRequestDocumentIds(documentIdSupplier, documentSetId).andThen(
            DoWriteDocuments())))

    override def start(uploadedFileId: Long) = steps.generate(uploadedFileId)
  }
}