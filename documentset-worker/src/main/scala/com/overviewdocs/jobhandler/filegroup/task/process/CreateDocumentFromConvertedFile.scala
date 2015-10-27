package com.overviewdocs.jobhandler.filegroup.task.process

import scala.concurrent.ExecutionContext
import com.overviewdocs.util.BulkDocumentWriter
import akka.actor.ActorRef
import com.overviewdocs.jobhandler.filegroup.task.TimeoutGenerator

object CreateDocumentFromConvertedFile {
  def apply(documentSetId: Long, filename: String, timeoutGenerator: TimeoutGenerator,
            documentIdSupplier: ActorRef, bulkDocumentWriter: BulkDocumentWriter)(implicit executor: ExecutionContext) =
    new UploadedFileProcess {
      override protected val steps =
        DoCreateFileWithView(documentSetId, timeoutGenerator).andThen(
          DoCreateDocumentData(documentSetId).andThen(
            DoWriteDocuments(documentSetId, filename, documentIdSupplier, bulkDocumentWriter)))
    }
}
