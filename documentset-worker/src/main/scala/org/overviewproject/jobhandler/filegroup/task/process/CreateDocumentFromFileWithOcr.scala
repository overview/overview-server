package org.overviewproject.jobhandler.filegroup.task.process

import org.overviewproject.jobhandler.filegroup.task.TimeoutGenerator
import akka.actor.ActorRef
import org.overviewproject.util.BulkDocumentWriter
import scala.concurrent.ExecutionContext

object CreateDocumentFromFileWithOcr {

  def apply(documentSetId: Long, filename: String, language: String, timeoutGenerator: TimeoutGenerator,
            documentIdSupplier: ActorRef, bulkDocumentWriter: BulkDocumentWriter)(implicit executor: ExecutionContext) =
    new UploadedFileProcess {
      override protected val steps =
        DoCreatePdfFile(documentSetId, filename).andThen(
          DoExtractTextWithOcr(documentSetId, language, timeoutGenerator).andThen(
            DoRequestDocumentIds(documentIdSupplier, documentSetId, filename).andThen(
              DoWriteDocuments(documentSetId, filename, bulkDocumentWriter))))
    }
}