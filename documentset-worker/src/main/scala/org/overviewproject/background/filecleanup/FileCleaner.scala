package org.overviewproject.background.filecleanup

import akka.actor.{ Actor, ActorRef, Props}
import akka.pattern.pipe

import org.overviewproject.util.Logger

object FileCleanerProtocol {
  case class Clean(fileId: Long)
  case class CleanComplete(fileId: Long)
}


/**
 * Deletes the [[File]] with the specified `fileId`, and any associated data.
 * `File.referenceCount` is not checked, the assumption is that the caller 
 * really wants to delete the file.
 * The actor will start deletes for all files as the requests are received. It would
 * be friendly if the caller maintains a queue, to ensure only one deletion is in 
 * progress at a time.
 * 
 */
trait FileCleaner extends Actor {
  import context._
  import FileCleanerProtocol._

  protected val logger = Logger.forClass(getClass)
  protected val fileRemover: FileRemover

  /**
   * The caller will be notified when the deletion attempt is complete.
   * Any failures are logged, and the [[FileCleaner]] attempts to proceed as
   * if deletion is completed normally.
   */
  override def receive = {
    case Clean(fileId) => attemptDeleteFile(fileId) pipeTo sender
  }

  private def attemptDeleteFile(fileId: Long) = {
    fileRemover.deleteFile(fileId)
      .map(_ => CleanComplete(fileId))
      .recover {
        case t: Throwable => 
          logger.error("File deletion failed for file {}", fileId, t)
          CleanComplete(fileId)
      }
  }
}

object FileCleaner {
  
  def apply() = Props(new FileCleanerImpl)
  
  private class FileCleanerImpl extends FileCleaner {
    override protected val fileRemover = FileRemover()
  }
}
