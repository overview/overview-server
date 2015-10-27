package com.overviewdocs.jobhandler.filegroup.task.process

import scala.concurrent.ExecutionContext
import akka.actor.ActorRef
import com.overviewdocs.util.BulkDocumentWriter

object CreateDocumentsFromPdfPages {
  def apply(documentSetId: Long, filename: String, lang: String,
            documentIdSupplier: ActorRef, bulkDocumentWriter: BulkDocumentWriter)(implicit executor: ExecutionContext) = {
    new UploadedFileProcess {
      override protected val steps =
        DoCreatePdfFile(documentSetId, filename, lang).andThen(
          DoCreateDocumentDataForPages(documentSetId).andThen(
            DoWriteDocuments(documentSetId, filename, documentIdSupplier, bulkDocumentWriter)))
    }
  }
}
