package org.overviewproject.background.filegroupcleanup

import akka.actor.{ Actor, ActorRef, Props }
import org.overviewproject.util.Logger

object FileGroupRemovalRequestQueueProtocol {
  case class RemoveFileGroup(fileGroupId: Long)
}


/**
 * Queue for [[FileGroup]] removal requests. When one request is complete, the next one is sent.
 * 
 * No checks for duplicate requests are made
 */
trait FileGroupRemovalRequestQueue extends Actor {
  import FileGroupRemovalRequestQueueProtocol._
  import FileGroupCleanerProtocol._

  import scala.collection.mutable.Queue
  protected val requests: Queue[Long] = Queue[Long]() // The first element in the queue is in progress

  override def receive = {
    case RemoveFileGroup(fileGroupId) => {
      requests.enqueue(fileGroupId)

      if (readyToSubmitRequest) submitNextRequest
    }
    case CleanComplete(fileGroupId) => {
      requests.dequeue

      Logger.info(s"[FileGroupRemovalRequestQueue] ($fileGroupId) Removal complete. Queue size ${requests.size}")
      submitNextRequest
    }
  }

  private def readyToSubmitRequest: Boolean = requests.size == 1
  private def submitNextRequest: Unit = requests.headOption.map{ fileGroupId =>
    Logger.info(s"[FileGroupRemovalRequestQueue] ($fileGroupId) Removing file group")
    fileGroupCleaner ! Clean(fileGroupId)
  }
  
  protected val fileGroupCleaner: ActorRef
}

object FileGroupRemovalRequestQueue {
  def apply(fileGroupCleaner: ActorRef): Props = Props(new FileGroupRemovalRequestQueueImpl(fileGroupCleaner))
  
  private class FileGroupRemovalRequestQueueImpl(fileGroupCleanerActor: ActorRef) extends FileGroupRemovalRequestQueue {
    override protected val fileGroupCleaner = fileGroupCleanerActor
  }
}