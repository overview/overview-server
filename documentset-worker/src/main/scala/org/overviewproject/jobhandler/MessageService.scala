package org.overviewproject.jobhandler

import scala.concurrent.Future
import scala.util.Try



trait MessageService {
  /**
   *  Create a connection to the message queue.
   *  @returns `Success` if connection is  established, `Failure` otherwise
   *  @param messageDelivery will be called when a new message is received. The method
   *  should return a `Future` that will be completed when the job specified in the message
   *  has finished processing.
   *  @param failureHandler will be called if the connection fails.
   */
  def createConnection(messageDelivery: String => Future[Unit], failureHandler: Exception => Unit): Try[Unit]
  
  /** The name of the queue being listened to */
  val queueName: String

}
