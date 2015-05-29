package org.overviewproject.jobhandler.filegroup.task.process

import akka.actor.ActorRef
import org.overviewproject.util.BulkDocumentWriter

object CreateDocumentsFromConvertedFilePages {
  def apply(documentSetId: Long, filename: String,
      documentIdSupplier: ActorRef, bulkDocumentWriter: BulkDocumentWriter) = new UploadedFileProcess {
    override protected val steps =
      DoCreateFileWithView(documentSetId).andThen(
        DoCreatePdfPages(documentSetId).andThen(
          DoRequestDocumentIds(documentIdSupplier, documentSetId, filename).andThen(
            DoWriteDocuments(documentSetId, filename, bulkDocumentWriter))))
  }
}