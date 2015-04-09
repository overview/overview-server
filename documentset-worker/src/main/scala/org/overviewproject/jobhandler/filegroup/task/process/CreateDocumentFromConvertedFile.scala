package org.overviewproject.jobhandler.filegroup.task.process

import akka.actor.ActorRef

object CreateDocumentFromConvertedFile {
  def apply(documentSetId: Long, documentIdSupplier: ActorRef) = new UploadedFileProcess {
    override protected val steps =
      DoCreateFileWithView(documentSetId).andThen(
        DoExtractTextFromPdf(documentSetId).andThen(
          DoRequestDocumentIds (documentIdSupplier, documentSetId).andThen(
            DoWriteDocuments())))
  }
}