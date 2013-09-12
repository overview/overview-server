package org.overviewproject.jobhandler.filegroup

import org.overviewproject.jobhandler.{ MessageHandling, MessageQueueActor, MessageServiceComponentImpl }
import akka.actor.Props

import org.overviewproject.jobhandler.filegroup.MotherWorkerProtocol._


class ClusteringJobHandler extends MessageQueueActor[Command] with MessageServiceComponentImpl with MessageHandling[Command] {
  override val messageService = new MessageServiceImpl("clustering_queue")
  override def createMessageHandler: Props = Props[MotherWorker]
  override def convertMessage(message: String): Command = ConvertClusteringMessage(message)
}

object ClusteringJobHandler {

  def apply(): Props = Props[ClusteringJobHandler]

}