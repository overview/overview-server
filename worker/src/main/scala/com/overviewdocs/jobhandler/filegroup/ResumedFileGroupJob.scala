package com.overviewdocs.jobhandler.filegroup

import com.overviewdocs.models.FileGroup

case class ResumedFileGroupJob(
  fileGroup: FileGroup,
  progressState: FileGroupProgressState
) {
  def documentSetId: Long = fileGroup.addToDocumentSetId.get
  def isComplete: Boolean = fileGroup.nFiles.get == progressState.nFilesIngested
  def cancel: Unit = progressState.cancel.success(akka.Done)
}
