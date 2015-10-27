package com.overviewdocs.jobhandler.filegroup.task.process

import scala.concurrent.ExecutionContext
import com.overviewdocs.util.BulkDocumentWriter
import akka.actor.ActorRef

object CreateDocumentFromPdfFile {
  def apply(documentSetId: Long, filename: String, lang: String,
            documentIdSupplier: ActorRef, bulkDocumentWriter: BulkDocumentWriter)(implicit executor: ExecutionContext) =
    new UploadedFileProcess {
      override protected val steps =
        DoCreatePdfFile(documentSetId, filename, lang).andThen(
          DoCreateDocumentData(documentSetId).andThen(
            DoWriteDocuments(documentSetId, filename, documentIdSupplier, bulkDocumentWriter)))
    }
}
