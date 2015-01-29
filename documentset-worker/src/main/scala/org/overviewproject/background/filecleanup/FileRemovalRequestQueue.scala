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
