package org.overviewproject.jobhandler.filegroup

import akka.actor._
import org.overviewproject.jobhandler.filegroup.FileHandlerProtocol.ExtractText
import org.overviewproject.jobhandler.JobDone


trait TextExtractorComponent {
  val actorCreator: ActorCreator
  
  trait ActorCreator {
    def produceTextExtractor: Props
  }
}


object FileGroupMessageHandlerProtocol {
  sealed trait Command
  case class ProcessFileCommand(fileGroupId: Long, uploadedFileId: Long) extends Command
}

class FileGroupMessageHandler extends Actor {
  this: TextExtractorComponent =>

  import FileGroupMessageHandlerProtocol._

  def receive = {
    case ProcessFileCommand(fileGroupId, uploadedFileId) =>
      val fileHandler = context.actorOf(actorCreator.produceTextExtractor)
      fileHandler ! ExtractText(fileGroupId, uploadedFileId)
    case JobDone(fileGroupId) => context.parent ! JobDone(fileGroupId)
  }
}

trait TextExtractorComponentImpl extends TextExtractorComponent {

  class ActorCreatorImpl extends ActorCreator {
    override def produceTextExtractor: Props = Props[FileHandlerImpl]
  }
 override val actorCreator = new ActorCreatorImpl

}

object FileGroupMessageHandler {
  class FileGroupMessageHandlerImpl extends FileGroupMessageHandler with TextExtractorComponentImpl 
  
  def apply(): Props = Props[FileGroupMessageHandlerImpl]
}
