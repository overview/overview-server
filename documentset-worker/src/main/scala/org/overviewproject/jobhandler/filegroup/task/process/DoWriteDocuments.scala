package org.overviewproject.jobhandler.filegroup.task.process

import org.overviewproject.jobhandler.filegroup.task.step.TaskStep
import org.overviewproject.jobhandler.filegroup.task.step.WriteDocuments
import org.overviewproject.models.Document


object DoWriteDocuments {
  def apply(documentSetId: Long, filename: String) = new StepGenerator[Seq[Document], Unit] {

    override def generate(documents: Seq[Document]): TaskStep =
      WriteDocuments(documentSetId, filename, documents)

  }
}