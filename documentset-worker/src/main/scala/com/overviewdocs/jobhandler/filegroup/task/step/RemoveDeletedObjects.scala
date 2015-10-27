package com.overviewdocs.jobhandler.filegroup.task.step

import akka.actor.ActorSelection
import scala.concurrent.Future
import com.overviewdocs.background.filecleanup.FileRemovalRequestQueueProtocol._
import com.overviewdocs.background.filegroupcleanup.FileGroupRemovalRequestQueueProtocol._


/**
 * Submit requests to remove [[File]]s and [[FileGroup]]s that have been deleted.
 */
trait RemoveDeletedObjects extends TaskStep {
  protected val fileGroupId: Long

  protected val fileRemovalQueue: ActorSelection
  protected val fileGroupRemovalQueue: ActorSelection

  override def execute: Future[TaskStep] = {
    fileRemovalQueue ! RemoveFiles
    fileGroupRemovalQueue ! RemoveFileGroup(fileGroupId)

    Future.successful(FinalStep)
  }
}

object RemoveDeletedObjects {
  def apply(fileGroupId: Long, fileRemovalQueue: ActorSelection,
            fileGroupRemovalQueue: ActorSelection): RemoveDeletedObjects =
    new RemoveDeletedObjectsImpl(fileGroupId, fileRemovalQueue, fileGroupRemovalQueue)

  private class RemoveDeletedObjectsImpl(
    override protected val fileGroupId: Long,
    override protected val fileRemovalQueue: ActorSelection,
    override protected val fileGroupRemovalQueue: ActorSelection)
     extends RemoveDeletedObjects
}
