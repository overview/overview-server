package org.overviewproject.background.filecleanup

import akka.actor.{ Actor, ActorRef, FSM }
import akka.pattern.pipe
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.collection.immutable.Queue
import org.overviewproject.util.Logger
import DeletedFileCleanerFSM._
import akka.actor.Props


object DeletedFileCleanerProtocol {
  case object RemoveDeletedFiles
  case object FileRemovalComplete
}

object DeletedFileCleanerFSM {
  sealed trait State
  case object Idle extends State
  case object Scanning extends State
  case object Working extends State

  sealed trait Data
  case object NoRequest extends Data
  case class IdQueue(requester: ActorRef, fileIds: Iterable[Long]) extends Data
}

trait DeletedFileCleaner extends Actor with FSM[State, Data] {
  import DeletedFileCleanerProtocol._
  import FileCleanerProtocol._

  protected val deletedFileScanner: DeletedFileFinder
  protected val fileCleaner: ActorRef

  private case class ScanComplete(ids: Iterable[Long])

  startWith(Idle, NoRequest)

  when(Idle) {
    case Event(RemoveDeletedFiles, _) => {
      deletedFileScanner.deletedFileIds.map(ScanComplete) pipeTo self

      goto(Scanning) using IdQueue(sender, Iterable.empty)
    }
  }

  when(Scanning) {
    case Event(ScanComplete(id :: tail), IdQueue(r, _)) => {
      fileCleaner ! Clean(id)
      goto(Working) using IdQueue(r, tail)
    }
    case Event(ScanComplete(Nil), IdQueue(r, _)) => {
      r ! FileRemovalComplete
      goto(Idle) using NoRequest
    }
    case Event(RemoveDeletedFiles, _) => stay
  }

  when(Working) {
    case Event(CleanComplete(a), IdQueue(r, id :: tail)) => {
      fileCleaner ! Clean(id)
      stay using IdQueue(r, tail)
    }
    case Event(CleanComplete(_), IdQueue(r, Nil)) => {
      r ! FileRemovalComplete
      goto(Idle) using NoRequest
    }
    case Event(RemoveDeletedFiles, _) => stay
  }

  
  whenUnhandled {
    case Event(t, _) => {
      Logger.error("[FileRemover] Unexpected event while removing files", t)
      goto(Idle) using NoRequest
    }
  }
  
  onTransition {
    case Idle -> Scanning => Logger.info("[FileRemover] Scanning for deleted files")
    case Scanning -> Idle => Logger.info("[FileRemover] No deleted files found")
    case Scanning -> Working => Logger.info("[FileRemover] Starting removal of deleted files")
    case Working -> Idle => Logger.info("[FileRemover] Completed removal of deleted files")
  }
  
  private def removeDeletedFiles: Future[Unit] =
    deletedFileScanner.deletedFileIds.map { fileIds =>
      for {
        id <- fileIds
      } fileCleaner ! Clean(id)
    }
}

object DeletedFileCleaner {
  
  def apply(fileCleaner: ActorRef) = Props(new DeletedFileCleanerImpl(fileCleaner))
  
  class DeletedFileCleanerImpl(val fileCleaner: ActorRef) extends DeletedFileCleaner {
    override protected val deletedFileScanner = DeletedFileFinder()
  }
  
}