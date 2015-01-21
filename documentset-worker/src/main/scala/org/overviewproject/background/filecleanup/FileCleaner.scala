package org.overviewproject.background.filecleanup

import akka.actor.{ Actor, ActorRef }
import akka.pattern.pipe

object FileCleanerProtocol {
  case class Clean(fileId: Long)
  case class CleanComplete(fileId: Long)
  case class CleanFailed(fileId: Long, t: Throwable)
}


/**
 * Deletes the [[File]] with the specified `fileId`, and any associated data.
 * `File.referenceCount` is not checked, the assumption is that the caller 
 * really wants to delete the file.
 * The actor will start deletes for all files as the requests are received. It would
 * be friendly if the caller maintains a queue, to ensure only one deletion is in 
 * progress at a time.
 * 
 * 
 */
trait FileCleaner extends Actor {
  import context._
  import FileCleanerProtocol._

  protected val fileRemover: FileRemover

  /**
   * The caller will be notified when the deletion attempt is complete.
   * On succcess, a [[CleanComplete]] is sent, otherwise [[CleanFailed]].
   */
  override def receive = {
    case Clean(fileId) => attemptDeleteFile(fileId) pipeTo sender
  }

  private def attemptDeleteFile(fileId: Long) =
    fileRemover.deleteFile(fileId)
      .map(_ => CleanComplete(fileId))
      .recover {
        case t: Throwable => CleanFailed(fileId, t)
      }

}