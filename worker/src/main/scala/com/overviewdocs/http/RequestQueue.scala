package com.overviewdocs.http

import akka.actor._
import scala.collection.mutable.{Buffer,ListBuffer}
import scala.util.{Failure,Success}

/** Messages used when interacting with RequestQueue */
object RequestQueueProtocol {
  /** Add request to the end of the queue */
  case class AddToEnd(request: Request)

  /** Add request to the front of the queue */
  case class AddToFront(request: Request)

  /** Sent to requester when request completes. */
  case class HttpSuccess(response: Response)

  /** Sent to requester when an exception or other error occurs while trying to process request */
  case class HttpFailure(exception: Throwable)
}

/**
 * Maintains a queue of http GET requests, ensuring that only a fixed number are in progress
 * at any given time.
 * When requests are completed or fail, the RequestQueue sends a message to the
 * original requester. Requests can be added to the front or the end of the queue.
 * @param client handles the actual http request
 * @param maxInFlightRequests is the maximum number of requests to allow to be in progress concurrently.
 */
class RequestQueue(client: Client, maxInFlightRequests: Int) extends Actor {
  import RequestQueueProtocol._
  import context.dispatcher

  private case class RequestCompleted() // Message only used internally

  private var queuedRequests: Buffer[(ActorRef, Request)] = ListBuffer()
  private var inFlightRequests: Int = 0

  /** 
   *  A received request is submitted immediately if the number of inflight requests is smaller
   *  than `maxInFlightRequests`. Otherwise, it is added to the queue.
   */
  def receive = {
    case AddToEnd(request) if inFlightRequests < maxInFlightRequests => startRequest(sender, request)
    case AddToEnd(request) => queuedRequests.+=((sender, request))
    case AddToFront(request) if inFlightRequests < maxInFlightRequests => startRequest(sender, request)
    case AddToFront(request) => queuedRequests.+=:((sender, request))
    case RequestCompleted() => completeRequest
  }

  /** Shutdown the client and close all connections when the actor is stopped */
  override def postStop = client.shutdown

  private def startRequest(requester: ActorRef, request: Request): Unit = {
    inFlightRequests += 1
    client.get(request).onComplete {
      case Success(response) => {
        requester ! HttpSuccess(response)
        self ! RequestCompleted()
      }
      case Failure(exception) => {
        requester ! HttpFailure(exception)
        self ! RequestCompleted()
      }
    }
  }

  private def completeRequest: Unit = {
    inFlightRequests -= 1
    startNextRequest
  }

  /** Submit next request in queue */
  private def startNextRequest: Unit = {
    if (queuedRequests.nonEmpty) {
      val (requester: ActorRef, request: Request) = queuedRequests.remove(0)
      startRequest(requester, request)
    }
  }
}
