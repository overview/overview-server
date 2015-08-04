package com.overviewdocs.jobhandler.filegroup.task.process

import scala.concurrent.ExecutionContext
import com.overviewdocs.jobhandler.filegroup.task.step.CreatePdfPages
import com.overviewdocs.jobhandler.filegroup.task.step.DocumentData
import com.overviewdocs.jobhandler.filegroup.task.step.TaskStep
import com.overviewdocs.models.File

object DoCreatePdfPages {

  def apply(documentSetId: Long)(implicit executor: ExecutionContext) = new StepGenerator[File, Seq[DocumentData]] {
    
    override def generate(file: File): TaskStep = 
      CreatePdfPages(documentSetId, file, nextStepFn)
    
  }
}