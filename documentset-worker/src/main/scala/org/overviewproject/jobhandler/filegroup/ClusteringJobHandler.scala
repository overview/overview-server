package org.overviewproject.jobhandler.filegroup


import akka.actor._
import org.overviewproject.jobhandler.MessageQueueActorProtocol.StartListening
import org.overviewproject.jobhandler.filegroup.MotherWorkerProtocol._
import org.overviewproject.jobhandler.SynchronousMessageQueueActor

class ClusteringJobHandler extends Actor {
  val motherWorker = context.actorOf(MotherWorker())
  val fileGroupQueueListener = context.actorOf(SynchronousMessageQueueActor(motherWorker, 
      "/queue/file-group-commands", ConvertFileGroupMessage.apply))
  val clusteringCommandsListener = context.actorOf(SynchronousMessageQueueActor(motherWorker, 
      "/queue/clustering-commands", ConvertClusteringMessage.apply))
  
  
  def receive = {
    case StartListening => {
      fileGroupQueueListener ! StartListening
      clusteringCommandsListener ! StartListening
    }
  }
}

object ClusteringJobHandler {

  def apply(): Props = Props[ClusteringJobHandler]

}