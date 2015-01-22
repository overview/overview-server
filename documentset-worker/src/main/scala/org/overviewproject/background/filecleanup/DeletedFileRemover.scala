package org.overviewproject.background.filecleanup

import akka.actor.{ Actor, ActorRef, FSM }
import akka.pattern.pipe
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.collection.immutable.Queue

object DeletedFileRemoverProtocol {
  case object RemoveDeletedFiles
  case object FileRemovalComplete
}

object DeletedFileRemoverFSM {
  sealed trait State
  case object Idle extends State
  case object Scanning extends State
  case object Working extends State

  sealed trait Data
  case object NoRequest extends Data
  case class IdQueue(requester: ActorRef, fileIds: Iterable[Long]) extends Data
}

import DeletedFileRemoverFSM._

trait DeletedFileRemover extends Actor with FSM[State, Data] {
  import DeletedFileRemoverProtocol._
  import FileCleanerProtocol._

  protected val deletedFileScanner: DeletedFileScanner
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
    case Event(ScanComplete(_), IdQueue(r, _)) => {
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
    case Event(CleanComplete(_), IdQueue(r, _)) => {
      r ! FileRemovalComplete
      goto(Idle) using NoRequest
    }
    case Event(RemoveDeletedFiles, _) => stay
  }

  private def removeDeletedFiles: Future[Unit] =
    deletedFileScanner.deletedFileIds.map { fileIds =>
      for {
        id <- fileIds
      } fileCleaner ! Clean(id)
    }
}