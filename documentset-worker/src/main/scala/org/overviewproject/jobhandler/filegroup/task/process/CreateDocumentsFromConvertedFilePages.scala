package org.overviewproject.jobhandler.filegroup.task.process

import akka.actor.ActorRef

object CreateDocumentsFromConvertedFilePages extends UploadedFileProcessCreator {
  def create(documentSetId: Long, documentIdSupplier: ActorRef) = new UploadedFileProcess {
    override protected val steps =
      DoCreateFileWithView(documentSetId).andThen(
        DoCreatePdfPages().andThen(
          DoRequestDocumentIds(documentIdSupplier, documentSetId).andThen(
            DoWriteDocuments())))
  }
}