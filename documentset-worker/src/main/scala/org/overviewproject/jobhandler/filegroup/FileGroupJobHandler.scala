package org.overviewproject.jobhandler.filegroup

import akka.actor._

import org.overviewproject.jobhandler.{ MessageHandling, MessageQueueActor, MessageServiceComponentImpl }
import org.overviewproject.jobhandler.MessageQueueActor
import org.overviewproject.jobhandler.MessageServiceComponentImpl
import org.overviewproject.jobhandler.filegroup.TextExtractorProtocol._

import FileGroupMessageHandlerProtocol._

class FileGroupJobHandler(jobMonitor: ActorRef) extends MessageQueueActor[Command] with MessageServiceComponentImpl with MessageHandling[Command] {
  override val messageService = new MessageServiceImpl("/queue/file-group-commands")
  override def createMessageHandler: Props = FileGroupMessageHandler(jobMonitor)
  override def convertMessage(message: String): Command = ConvertFileGroupMessage(message)
}

object FileGroupJobHandler {
  def apply(jobMonitor: ActorRef): Props = Props(new FileGroupJobHandler(jobMonitor))
}
