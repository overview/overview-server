package org.overviewproject.http

import scala.collection.JavaConverters._
import com.ning.http.client.{ AsyncCompletionHandler, Response }
import akka.actor._
import scala.concurrent.duration.FiniteDuration
import scala.collection.mutable.HashMap

/** Messages used when interacting with RequestQueue */
object RequestQueueProtocol {
  /** Add request to the end of the queue */
  case class AddToEnd(request: Request)

  /** Add request to the front of the queue */
  case class AddToFront(request: Request)

  /** Sent to requester when request completes. */
  case class Result(response: SimpleResponse)

  /** Sent to requester when an exception or other error occurs while trying to process request */
  case class Failure(t: Throwable)
}

/**
 * SimpleResponse implementation that provides the response information we're interested in
 */
class AsyncHttpClientResponse(response: Response) extends SimpleResponse {
  override def status: Int = response.getStatusCode
  override def body: String = response.getResponseBody

  /** @param name will match case-insensitively */
  override def headers(name: String): Seq[String] = response.getHeaders(name).asScala.toSeq
  override def headersToString: String =
    response.getHeaders.iterator.asScala.map { h => s"${h.getKey}:${h.getValue.asScala.mkString(",")}" }.mkString("\r\n")

}

/**
 * Maintains a queue of http GET requests, ensuring that only a fixed number are in progress
 * at any given time.
 * When requests are completed or fail, the RequestQueue sends a message to the
 * original requester. Requests can be added to the front or the end of the queue.
 * @param client handles the actual http request
 * @param maxInFlightRequests is the maximum number of requests to allow to be in progress concurrently.
 * @param superTimeout AsyncHttpClient seems to leave request hanging and never timing out. Wait for the superTimeout
 * before canceling the request.
 */
class RequestQueue(client: Client, maxInFlightRequests: Int, superTimeout: FiniteDuration) extends Actor {
  import RequestQueueProtocol._
  private case class RequestCompleted(requestId: Long) // Message only used internally
  private case class CancelRequest(requestId: Long) // Cancel stuck request


  /**
   * Container for monitoring a request and canceling it on superTimeout
   * 
   * @param monitor The scheduled event that will trigger request cancellation
   * @param request The Request being monitored
   */
  private class RequestMonitor(monitor: Cancellable, request: Request) {
    /** Stop the monitor from triggering the cancellation event */
    def cancelMonitor: Unit = monitor.cancel
    
    /** Cancel the request */
    def cancelRequest: Unit = request.cancel
  }
  
  private var queuedRequests: Seq[(ActorRef, Request)] = Seq.empty // FIXME: mutable seq will be more efficient with many requests
  private var inFlightRequests: scala.collection.mutable.Map[Long, RequestMonitor] = HashMap.empty
  private var nextInFlightRequestId: Long = 0

  /**
   *  When requests complete, notify requester and process any queued requests
   */
  private class ResultHandler(requestId: Long, requester: ActorRef) extends AsyncCompletionHandler[Unit] {
    override def onCompleted(response: Response): Unit = {
      requester ! Result(new AsyncHttpClientResponse(response))
      self ! RequestCompleted(requestId)
    }

    override def onThrowable(t: Throwable): Unit = { 
      requester ! Failure(t)
      self ! RequestCompleted(requestId) // Try to continue, and let requester handle the error
    }
  }

  /** 
   *  A received request is submitted immediately if the number of inflight requests is smaller
   *  than `maxInFlightRequests`. Otherwise, it is added to the queue.
   */
  def receive = {
    case AddToEnd(request) if inFlightRequests.size < maxInFlightRequests => submitRequest(sender, request)
    case AddToEnd(request) => queueRequest(sender, request)
    case AddToFront(request) if inFlightRequests.size < maxInFlightRequests => submitRequest(sender, request)
    case AddToFront(request) => queueRequestInFront(sender, request)
    case RequestCompleted(requestId) => completeRequest(requestId)
    case CancelRequest(requestId) => cancelRequest(requestId)
  }

  /** Shutdown the client and close all connections when the actor is stopped */
  override def postStop = client.shutdown()


  private def submitRequest(requester: ActorRef, request: Request): Unit = {
    request.execute(client, new ResultHandler(nextInFlightRequestId, requester))
    addToInFlightRequests(request)
  }

  private def queueRequest(requester: ActorRef, url: Request): Unit = 
    queuedRequests = queuedRequests :+ (requester, url)

  private def queueRequestInFront(requester: ActorRef, url: Request): Unit = 
    queuedRequests = (requester, url) +: queuedRequests

  private def completeRequest(requestId: Long): Unit = {
    removeFromInFlightRequests(requestId)
    startNextRequest
  }

  private def cancelRequest(requestId: Long): Unit = inFlightRequests.get(requestId).map { _.cancelRequest }
  
  /**
   * Schedule an event to trigger request cancellation if the request lives past
   * the superTimeout
   */
  private def addToInFlightRequests(request: Request): Unit = {
    import context.dispatcher

    val superTimeoutEvent = context.system.scheduler.scheduleOnce(superTimeout, self, CancelRequest(nextInFlightRequestId))
    inFlightRequests += (nextInFlightRequestId -> new RequestMonitor(superTimeoutEvent, request))

    nextInFlightRequestId += 1
  }
  
  /** Stop the cancellation event for a request */
  private def removeFromInFlightRequests(requestId: Long): scala.collection.mutable.Map[Long, RequestMonitor] = {
    inFlightRequests.get(requestId).map { _.cancelMonitor }
    inFlightRequests -= requestId
  }
  
  
  /** Submit next request in queue */
  private def startNextRequest: Unit = 
    for ((requester, request) <- queuedRequests.headOption) { 
       submitRequest(requester, request)
       queuedRequests = queuedRequests.tail
     }


}