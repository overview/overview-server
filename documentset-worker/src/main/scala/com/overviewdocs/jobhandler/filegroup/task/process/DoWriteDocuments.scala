package com.overviewdocs.jobhandler.filegroup.task.process

import akka.actor.ActorRef
import scala.concurrent.ExecutionContext

import com.overviewdocs.jobhandler.filegroup.task.step.{DocumentWithoutIds,TaskStep,WriteDocuments}
import com.overviewdocs.util.BulkDocumentWriter

object DoWriteDocuments {
  def apply(documentSetId: Long, filename: String, documentIdSupplier: ActorRef,
            bulkDocumentWriter: BulkDocumentWriter)(implicit executor: ExecutionContext) = {
    new StepGenerator[Seq[DocumentWithoutIds], Unit] {
      override def generate(documentsWithoutIds: Seq[DocumentWithoutIds]): TaskStep = {
        new WriteDocuments(documentSetId, filename, documentsWithoutIds, documentIdSupplier, bulkDocumentWriter)
      }
    }
  }
}
