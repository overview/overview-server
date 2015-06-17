package org.overviewproject.jobhandler.filegroup.task.process

import scala.concurrent.ExecutionContext
import akka.actor.ActorRef
import org.overviewproject.util.BulkDocumentWriter



object CreateDocumentFromPdfPage {
  def apply(documentSetId: Long, filename: String,
            documentIdSupplier: ActorRef, bulkDocumentWriter: BulkDocumentWriter)(implicit executor: ExecutionContext) =
    new UploadedFileProcess {
      override protected val steps =
        DoCreatePdfFile(documentSetId, filename).andThen(
          DoCreatePdfPages(documentSetId).andThen(
            DoRequestDocumentIds(documentIdSupplier, documentSetId, filename).andThen(
              DoWriteDocuments(documentSetId, filename, bulkDocumentWriter))))

    }
}