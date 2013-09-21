package org.overviewproject.jobhandler.filegroup

import akka.actor._
import org.overviewproject.jobhandler.filegroup.FileHandlerProtocol.ExtractText
import org.overviewproject.jobhandler.JobProtocol._
import org.overviewproject.jobhandler.MessageHandlerProtocol._

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

class FileGroupMessageHandler(jobMonitor: ActorRef) extends Actor {
  this: TextExtractorComponent =>

  import FileGroupMessageHandlerProtocol._

  def receive = {
    case ProcessFileCommand(fileGroupId, uploadedFileId) =>
      val fileHandler = context.actorOf(actorCreator.produceTextExtractor)
      fileHandler ! ExtractText(fileGroupId, uploadedFileId)
      jobMonitor ! JobStart(fileGroupId)
    case JobDone(fileGroupId) => {
      jobMonitor ! JobDone(fileGroupId)
      context.parent ! MessageHandled
    }
  }
}

trait TextExtractorComponentImpl extends TextExtractorComponent {

  class ActorCreatorImpl extends ActorCreator {
    override def produceTextExtractor: Props = Props[FileHandlerImpl]
  }
 override val actorCreator = new ActorCreatorImpl

}

object FileGroupMessageHandler {
  class FileGroupMessageHandlerImpl(jobMonitor: ActorRef) extends FileGroupMessageHandler(jobMonitor) with TextExtractorComponentImpl 
  
  def apply(jobMonitor: ActorRef): Props = Props(new FileGroupMessageHandlerImpl(jobMonitor))
}
