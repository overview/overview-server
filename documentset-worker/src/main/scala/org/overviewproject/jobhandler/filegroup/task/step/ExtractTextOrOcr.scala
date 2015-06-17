package org.overviewproject.jobhandler.filegroup.task.step

import org.overviewproject.models.File

trait ExtractTextOrOcr extends UploadedFileProcessStep {

  protected val file: File
  
  protected val nextStep: Seq[DocumentData] => TaskStep
}