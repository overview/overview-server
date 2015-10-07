package com.overviewdocs.jobhandler.filegroup.task.process

import scala.concurrent.ExecutionContext
import com.overviewdocs.util.BulkDocumentWriter
import akka.actor.ActorRef

object CreateDocumentFromFileWithOcr {

  def apply(documentSetId: Long, filename: String, language: String,
            documentIdSupplier: ActorRef, bulkDocumentWriter: BulkDocumentWriter)(implicit executor: ExecutionContext) =
    new UploadedFileProcess {
      override protected val steps =
        DoCreatePdfFile(documentSetId, filename).andThen(
          DoExtractTextWithOcr(documentSetId, language).andThen(
            DoCreateDocumentData(documentSetId, true).andThen(
              DoRequestDocumentIds (documentIdSupplier, documentSetId, filename).andThen(
                DoWriteDocuments(documentSetId, filename, bulkDocumentWriter)))))
    }
}
