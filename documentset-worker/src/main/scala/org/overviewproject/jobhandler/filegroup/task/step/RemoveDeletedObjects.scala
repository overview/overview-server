package org.overviewproject.jobhandler.filegroup.task.step

import akka.actor.ActorSelection
import scala.concurrent.Future
import org.overviewproject.background.filecleanup.FileRemovalRequestQueueProtocol._
import org.overviewproject.background.filegroupcleanup.FileGroupRemovalRequestQueueProtocol._

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
  def apply(fileGroupId: Long, fileRemovalQueue: ActorSelection, fileGroupRemovalQueue: ActorSelection): RemoveDeletedObjects = 
   new RemoveDeletedObjectsImpl(fileGroupId, fileRemovalQueue, fileGroupRemovalQueue)
  
  private class RemoveDeletedObjectsImpl(
     override protected val fileGroupId: Long,
     override protected val fileRemovalQueue: ActorSelection,
     override protected val fileGroupRemovalQueue: ActorSelection
  ) extends RemoveDeletedObjects
}