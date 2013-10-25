package org.overviewproject.messagequeue.apollo

import akka.actor._
import org.overviewproject.messagequeue.{ MessageContainer, MessageReceiver }


object ApolloMessageReceiver {

  def apply[T](messageRecipient: ActorRef, 
               queueName: String, 
               messageConverter: String => T): Props = {
    val messageService = new ApolloMessageService(queueName)
    
    Props(new MessageReceiver[T](messageRecipient, messageService, messageConverter))
  }
}