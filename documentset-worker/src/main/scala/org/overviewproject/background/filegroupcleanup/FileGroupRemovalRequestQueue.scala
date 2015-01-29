package org.overviewproject.background.filegroupcleanup

import akka.actor.{ Actor, ActorRef }

object FileGroupRemovalRequestQueueProtocol {
  case class RemoveFileGroup(fileGroupId: Long)
}

trait FileGroupRemovalRequestQueue extends Actor {
  import FileGroupRemovalRequestQueueProtocol._
  import FileGroupCleanerProtocol._

  import scala.collection.mutable.Queue
  protected val requests: Queue[Long] = Queue[Long]()

  override def receive = {
    case RemoveFileGroup(fileGroupId) => {
      requests.enqueue(fileGroupId)

      if (readyToSubmitRequest) submitNextRequest
    }
    case CleanComplete(fileGroupId) => {
      requests.dequeue
      submitNextRequest
    }
  }

  private def readyToSubmitRequest: Boolean = requests.size == 1
  private def submitNextRequest: Unit = requests.headOption.map(fileGroupCleaner ! Clean(_))
  
  protected val fileGroupCleaner: ActorRef
}
