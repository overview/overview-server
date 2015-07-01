package org.overviewproject.background.filecleanup

import akka.actor.{ Actor, ActorRef, FSM }
import akka.pattern.pipe
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.collection.immutable.Queue
import DeletedFileCleanerFSM._
import akka.actor.Props

import org.overviewproject.util.Logger

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


/**
 * When a [[RemoveDeletedFiles]] request is received, the [[DeletedFileCleaner]] finds
 * all [[File]]s with `referenceCount = 0`. Each [[File]] found is sent, sequentially,
 * to the [[FileCleaner]], which performs the actual removal of data.
 * 
 * Any requests received while a previous request is being processed are ignored.
 */
trait DeletedFileCleaner extends Actor with FSM[State, Data] {
  import DeletedFileCleanerProtocol._
  import FileCleanerProtocol._

  protected val logger = Logger.forClass(getClass)
  protected val deletedFileFinder: DeletedFileFinder
  protected val fileCleaner: ActorRef

  private case class ScanComplete(ids: Iterable[Long])

  startWith(Idle, NoRequest)

  when(Idle) {
    case Event(RemoveDeletedFiles, _) => {
      deletedFileFinder.deletedFileIds.map(ScanComplete) pipeTo self

      goto(Scanning) using IdQueue(sender, Iterable.empty)
    }
  }

  when(Scanning) {
    case Event(ScanComplete(ids), IdQueue(r, _)) if ids.nonEmpty => {
      fileCleaner ! Clean(ids.head)
      goto(Working) using IdQueue(r, ids.tail)
    }
    case Event(ScanComplete(_), IdQueue(r, _)) => {
      r ! FileRemovalComplete
      goto(Idle) using NoRequest
    }
    case Event(RemoveDeletedFiles, _) => stay
  }

  when(Working) {
    case Event(CleanComplete(a), IdQueue(r, ids)) if ids.nonEmpty => {
      fileCleaner ! Clean(ids.head)
      stay using IdQueue(r, ids.tail)
    }
    case Event(CleanComplete(_), IdQueue(r, _)) => {
      r ! FileRemovalComplete
      goto(Idle) using NoRequest
    }
    case Event(RemoveDeletedFiles, _) => stay
  }

  
  whenUnhandled {
    case Event(t, _) => {
      logger.error("Unexpected event while removing files", t)
      goto(Idle) using NoRequest
    }
  }
  
  onTransition {
    case Idle -> Scanning => logger.info("Scanning for deleted files")
    case Scanning -> Idle => logger.info("No deleted files found")
    case Scanning -> Working => logger.info("Starting removal of deleted files")
    case Working -> Idle => logger.info("Completed removal of deleted files")
  }
  
  private def removeDeletedFiles: Future[Unit] =
    deletedFileFinder.deletedFileIds.map { fileIds =>
      for {
        id <- fileIds
      } fileCleaner ! Clean(id)
    }
}

object DeletedFileCleaner {
  
  def apply(fileCleaner: ActorRef) = Props(new DeletedFileCleanerImpl(fileCleaner))
  
  class DeletedFileCleanerImpl(val fileCleaner: ActorRef) extends DeletedFileCleaner {
    override protected val deletedFileFinder = DeletedFileFinder
  }
}
