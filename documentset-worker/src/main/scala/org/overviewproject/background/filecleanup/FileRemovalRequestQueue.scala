package org.overviewproject.background.filecleanup

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.FSM


import FileRemovalRequestQueueFSM._
import akka.actor.Props


object FileRemovalRequestQueueProtocol {
  case object RemoveFiles  
}


object FileRemovalRequestQueueFSM {
  sealed trait State
  case object Working extends State
  case object Idle extends State
  
  sealed trait Data
  case class RequestPending(isPending: Boolean) extends Data
}


/**
 * Receives requests to remove deleted [[File]]s and associated data
 * Currently, request do not include ids, so the entire `file` table will
 * be scanned to find [[Files]]s with `referenceCount = 0.
 * 
 * A request is forwarded to a [[DeletedFileCleaner]], which performs the scan 
 * and initiates the removal of data. If requests are received while the [[DeletedFileCleaner]]
 * is working, one request will be submitted to the [[DeletedFileCleaner]] after it completes
 * its current request.
 * 
 * When the [[FileRemovalRequestQueue]] starts up, it sends a request to the [[DeletedFileCleaner]],
 * ensuring that all deleted [[File]] data is removed even if previous requests have been lost or 
 * interrupted.
 */
trait FileRemovalRequestQueue extends Actor with FSM[State, Data] {
  import FileRemovalRequestQueueProtocol._
  import DeletedFileCleanerProtocol._
  
  protected val fileRemover: ActorRef
  
  override def preStart(): Unit = fileRemover ! RemoveDeletedFiles

  startWith(Working, RequestPending(false))
  
  when(Idle) {
    case Event(RemoveFiles, _) => {
      fileRemover ! RemoveDeletedFiles
      goto(Working) 
    }  
  }
  
  when(Working) {
    case Event(FileRemovalComplete, RequestPending(true)) => {
      fileRemover ! RemoveDeletedFiles
      stay using RequestPending(false)
    }
    case Event(FileRemovalComplete, RequestPending(false)) => goto(Idle)
    case Event(RemoveFiles, _) => stay using RequestPending(true)
  }
  
}

object FileRemovalRequestQueue {
  def apply(fileRemover: ActorRef) = Props(new FileRemovalQueueImpl(fileRemover))
  
  class FileRemovalQueueImpl(val fileRemover: ActorRef) extends FileRemovalRequestQueue 

}
