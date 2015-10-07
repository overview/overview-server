package com.overviewdocs.jobhandler.filegroup.task.process

import scala.concurrent.ExecutionContext
import com.overviewdocs.util.BulkDocumentWriter
import akka.actor.ActorRef

object CreateDocumentsFromPagesWithOcr {

  def apply(documentSetId: Long, filename: String, language: String,
            documentIdSupplier: ActorRef, bulkDocumentWriter: BulkDocumentWriter)(implicit executor: ExecutionContext) = {
    new UploadedFileProcess {
      override protected val steps =
        DoCreatePdfFile(documentSetId, filename).andThen(
          DoExtractTextWithOcr(documentSetId, language).andThen(
            DoCreateDocumentDataForPages(documentSetId).andThen(
              DoRequestDocumentIds (documentIdSupplier, documentSetId, filename).andThen(
                DoWriteDocuments(documentSetId, filename, bulkDocumentWriter)))))
    }
  }
}
