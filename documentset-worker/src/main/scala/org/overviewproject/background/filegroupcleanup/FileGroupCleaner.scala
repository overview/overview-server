package org.overviewproject.background.filegroupcleanup

import akka.actor.{ Actor, Props }
import akka.pattern.pipe
import org.overviewproject.util.Logger

object FileGroupCleanerProtocol {
  case class Clean(fileGroupId: Long)
  case class CleanComplete(fileGroupId: Long)
}

/**
 * Removes all data associated with the specified [[FileGroup]] when a request is received.
 */
trait FileGroupCleaner extends Actor {
  import context._
  import FileGroupCleanerProtocol._
  
  override def receive = {
    case Clean(fileGroupId) => attemptFileGroupRemoval(fileGroupId) pipeTo sender
  }
  
  private def attemptFileGroupRemoval(fileGroupId: Long) = 
    fileGroupRemover.remove(fileGroupId)
      .map(_ => CleanComplete(fileGroupId))
      .recover {
    case t: Throwable =>
      Logger.error(s"FileGroup removal failed for FileGroup $fileGroupId", t)
      CleanComplete(fileGroupId)
  }
  
  protected val fileGroupRemover: FileGroupRemover
  
}

object FileGroupCleaner {
  def apply(): Props = Props(new FileGroupCleanerImpl)
  
  private class FileGroupCleanerImpl extends FileGroupCleaner {
    override protected val fileGroupRemover = FileGroupRemover()
  }
}