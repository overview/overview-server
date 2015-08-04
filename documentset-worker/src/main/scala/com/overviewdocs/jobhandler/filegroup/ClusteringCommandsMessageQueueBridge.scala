package com.overviewdocs.jobhandler.filegroup

import akka.actor._
import com.overviewdocs.messagequeue.apollo.ApolloMessageReceiver


object ClusteringCommandsMessageQueueBridge {
  private val ClusteringCommandQueue = "/queue/clustering-commands"
    
  def apply(recipient: ActorRef): Props = 
    ApolloMessageReceiver(recipient, ClusteringCommandQueue, ConvertClusteringMessage.apply)
}