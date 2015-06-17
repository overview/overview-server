package org.overviewproject.jobhandler.filegroup.task.process

import scala.concurrent.ExecutionContext
import org.overviewproject.jobhandler.filegroup.task.step.TaskStep
import org.overviewproject.jobhandler.filegroup.task.step.WriteDocuments
import org.overviewproject.models.Document
import org.overviewproject.util.BulkDocumentWriter

object DoWriteDocuments {
  def apply(documentSetId: Long, filename: String,
            bulkDocumentWriter: BulkDocumentWriter)(implicit executor: ExecutionContext) = new StepGenerator[Seq[Document], Unit] {

    override def generate(documents: Seq[Document]): TaskStep =
      WriteDocuments(documentSetId, filename, documents, bulkDocumentWriter)

  }
}