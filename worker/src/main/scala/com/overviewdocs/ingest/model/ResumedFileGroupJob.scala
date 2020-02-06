package com.overviewdocs.ingest.model

import akka.actor.ActorRef
import com.overviewdocs.models.FileGroup

case class ResumedFileGroupJob(
  fileGroup: FileGroup,
  progressState: FileGroupProgressState,
  onCompleteSendToActor: ActorRef,
  onCompleteMessage: Any
) {
  def fileGroupId: Long = fileGroup.id
  def documentSetId: Long = fileGroup.addToDocumentSetId.get
  def isComplete: Boolean = fileGroup.nFiles.get == progressState.nFilesIngested
  def isCanceled: Boolean = progressState.cancel.isCompleted || fileGroup.deleted

  def cancel: Unit = {
    progressState.cancel.trySuccess(akka.Done)
  }

  override def hashCode = fileGroup.hashCode
}
