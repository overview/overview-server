package com.overviewdocs.ingest.models

import com.overviewdocs.models.FileGroup

case class ResumedFileGroupJob(
  fileGroup: FileGroup,
  progressState: FileGroupProgressState,
  onComplete: () => Unit
) {
  def fileGroupId: Long = fileGroup.id
  def documentSetId: Long = fileGroup.addToDocumentSetId.get
  def isComplete: Boolean = fileGroup.nFiles.get == progressState.nFilesIngested
  def cancel: Unit = progressState.cancel.success(akka.Done)
  def isCanceled: Boolean = progressState.cancel.isCompleted
}
