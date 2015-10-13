package com.overviewdocs.jobhandler.filegroup.task.process

import scala.concurrent.ExecutionContext
import com.overviewdocs.models.File
import com.overviewdocs.jobhandler.filegroup.task.step.DocumentWithoutIds
import com.overviewdocs.jobhandler.filegroup.task.step.TaskStep
import com.overviewdocs.jobhandler.filegroup.task.step.CreateDocumentDataForPages

object DoCreateDocumentDataForPages {

  def apply(documentSetId: Long)(implicit executor: ExecutionContext) = {
    new StepGenerator[(File, Seq[(String,Boolean)]), Seq[DocumentWithoutIds]] {
      override def generate(documentInfo: (File, Seq[(String,Boolean)])): TaskStep = {
        CreateDocumentDataForPages(documentSetId, nextStepFn, documentInfo._1, documentInfo._2)
      }
    }
  }
}
