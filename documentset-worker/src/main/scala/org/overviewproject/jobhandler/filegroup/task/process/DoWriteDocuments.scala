package com.overviewdocs.jobhandler.filegroup.task.process

import scala.concurrent.ExecutionContext
import com.overviewdocs.jobhandler.filegroup.task.step.TaskStep
import com.overviewdocs.jobhandler.filegroup.task.step.WriteDocuments
import com.overviewdocs.models.Document
import com.overviewdocs.util.BulkDocumentWriter

object DoWriteDocuments {
  def apply(documentSetId: Long, filename: String,
            bulkDocumentWriter: BulkDocumentWriter)(implicit executor: ExecutionContext) = new StepGenerator[Seq[Document], Unit] {

    override def generate(documents: Seq[Document]): TaskStep =
      WriteDocuments(documentSetId, filename, documents, bulkDocumentWriter)

  }
}