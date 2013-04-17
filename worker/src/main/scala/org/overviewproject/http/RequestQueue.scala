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
  private case class RequestCompleted(url: String) // Message only used internally
  private case class CancelRequest(url: String) // Cancel stuck request

  private var inFlightRequestCount: Int = 0
  private var queuedRequests: Seq[(ActorRef, Request)] = Seq.empty // FIXME: mutable seq will be more efficient with many requests
  private var inFlightRequests: scala.collection.mutable.Map[String, (Cancellable, Request)] = HashMap.empty

  /**
   *  When requests complete, notify requester and process any queued requests
   */
  private class ResultHandler(url: String, requester: ActorRef) extends AsyncCompletionHandler[Unit] {
    override def onCompleted(response: Response): Unit = {
      requester ! Result(new AsyncHttpClientResponse(response))
      self ! RequestCompleted(url)
    }

    override def onThrowable(t: Throwable): Unit = {
      requester ! Failure(t)
      self ! RequestCompleted(url) // Try to continue, and let requester handle the error
    }
  }

  def receive = {
    case AddToEnd(request) if inFlightRequestCount < maxInFlightRequests => submitRequest(sender, request)
    case AddToEnd(request) => queueRequest(sender, request)
    case AddToFront(request) if inFlightRequestCount < maxInFlightRequests => submitRequest(sender, request)
    case AddToFront(request) => queueRequestInFront(sender, request)
    case RequestCompleted(url) => handleNextRequest(url)
    case CancelRequest(url) => cancelRequest(url)
  }

  override def postStop = client.shutdown()

  private def submitRequest(requestor: ActorRef, request: Request): Unit = {
    request.execute(client, new ResultHandler(request.url, requestor))

    import context.dispatcher
    val superTimeoutEvent = context.system.scheduler.scheduleOnce(superTimeout, self, CancelRequest(request.url))
    inFlightRequests += (request.url -> (superTimeoutEvent, request))

    inFlightRequestCount += 1
  }

  private def queueRequest(requestor: ActorRef, url: Request): Unit = queuedRequests = queuedRequests :+ (sender, url)

  private def queueRequestInFront(requestor: ActorRef, url: Request): Unit = queuedRequests = (requestor, url) +: queuedRequests

  private def handleNextRequest(url: String): Unit = {
    inFlightRequestCount -= 1
    inFlightRequests.get(url).map { r => r._1.cancel }
    
    queuedRequests.headOption.map { r =>
      submitRequest(r._1, r._2)
      queuedRequests = queuedRequests.tail
    }
  }

  private def cancelRequest(url: String): Unit = inFlightRequests.get(url).map { r => r._2.cancel }

}