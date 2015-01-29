package org.overviewproject.background.filegroupcleanup

import akka.actor.{ Actor, ActorRef }
import org.overviewproject.background.filegroupcleanup.FileGroupRemovalRequestQueueProtocol._

trait DeletedFileGroupCleaner extends Actor {
  import context._

  private case object RemoveDeletedFileGroups

  override def preStart = self ! RemoveDeletedFileGroups

  override def receive = {
    case RemoveDeletedFileGroups => {
      deletedFileGroupFinder.deletedFileGroupIds.map {
        _.foreach(fileGroupRemovalRequestQueue ! RemoveFileGroup(_))
      }
      context.stop(self)
    }

  }

  protected val deletedFileGroupFinder: DeletedFileGroupFinder
  protected val fileGroupRemovalRequestQueue: ActorRef
}