package org.overviewproject.background.filegroupcleanup

import scala.concurrent.Future
import akka.actor.{ Actor, ActorRef, Props }
import akka.pattern.pipe
import org.overviewproject.background.filegroupcleanup.FileGroupRemovalRequestQueueProtocol._


/**
 * Looks for deleted [[FileGroup]]s on start up, and sends removal requests to
 * the [[FileGroupRemovalRequestQueue]].
 * 
 * When requests have been sent, the actor terminates. This functionality probably
 * doesn't need to be an actor.
 */
trait DeletedFileGroupCleaner extends Actor {
  import context._

  protected case object RemoveDeletedFileGroups
  protected case object RequestsSent

  override def preStart = self ! RemoveDeletedFileGroups

  override def receive = {
    case RemoveDeletedFileGroups => requestRemovals pipeTo self
    case RequestsSent =>  context.stop(self)
  }

  private def requestRemovals =
    deletedFileGroupFinder.deletedFileGroupIds.map { ids =>
      ids.foreach(fileGroupRemovalRequestQueue ! RemoveFileGroup(_))
      RequestsSent
    }

  protected val deletedFileGroupFinder: DeletedFileGroupFinder
  protected val fileGroupRemovalRequestQueue: ActorRef
}


object DeletedFileGroupCleaner {
  def apply(fileGroupRemovalRequestQueue: ActorRef): Props = 
    Props(new DeletedFileGroupCleanerImpl(fileGroupRemovalRequestQueue))
  
  private class DeletedFileGroupCleanerImpl(fileGroupRemovalRequestQueueActor: ActorRef) extends DeletedFileGroupCleaner {
    override protected val deletedFileGroupFinder = DeletedFileGroupFinder()
    override protected val fileGroupRemovalRequestQueue = fileGroupRemovalRequestQueueActor
  }
}