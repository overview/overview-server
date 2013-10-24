package org.overviewproject.jobhandler.filegroup


import akka.actor._
import org.overviewproject.jobhandler.filegroup.MotherWorkerProtocol._
import org.overviewproject.messagequeue.apollo.ApolloMessageReceiver
import org.overviewproject.jobhandler.MessageQueueActorProtocol.RegisterWith

class ClusteringJobHandler extends Actor {
  val motherWorker = context.actorOf(MotherWorker())
  val fileGroupQueueListener = context.actorOf(ApolloMessageReceiver(motherWorker, 
      "/queue/file-group-commands", ConvertFileGroupMessage.apply))
  val clusteringCommandsListener = context.actorOf(ApolloMessageReceiver(motherWorker, 
      "/queue/clustering-commands", ConvertClusteringMessage.apply))
  
  
  def receive = {
    case r: RegisterWith => {
      fileGroupQueueListener ! r
      clusteringCommandsListener ! r
    }
  }
}

object ClusteringJobHandler {

  def apply(): Props = Props[ClusteringJobHandler]

}