package org.overviewproject.http

import scala.collection.JavaConverters._

import akka.actor._
import com.ning.http.client.AsyncCompletionHandler
import com.ning.http.client.Response

object RequestQueueProtocol {
  case class AddToEnd(request: Request)
  case class AddToFront(request: Request)
  case class Result(response: SimpleResponse)
  case class Failure(t: Throwable)
}

class AsyncHttpClientResponse(response: Response) extends SimpleResponse {
  override def status: Int = response.getStatusCode
  override def body: String = response.getResponseBody
  override def headers(name: String): Seq[String] = response.getHeaders(name).asScala.toSeq
}

class RequestQueue(client: Client, maxInFlightRequests: Int) extends Actor {
  import RequestQueueProtocol._
  private case class RequestCompleted()

  private var inFlightRequests: Int = 0
  private var queuedRequests: Seq[(ActorRef, Request)] = Seq.empty // FIXME: mutable seq will be more efficient with many requests

  class ResultHandler(requestor: ActorRef) extends AsyncCompletionHandler[Unit] {
    override def onCompleted(response: Response): Unit = {
      requestor ! Result(new AsyncHttpClientResponse(response))
      self ! RequestCompleted()
    }
    
    override def onThrowable(t: Throwable): Unit = {
      requestor ! Failure(t)
      self ! RequestCompleted()
    }
  }

  def receive = {
    case AddToEnd(request) if inFlightRequests < maxInFlightRequests => submitRequest(sender, request)
    case AddToEnd(request) => queueRequest(sender, request)
    case AddToFront(request) if inFlightRequests < maxInFlightRequests => submitRequest(sender, request)
    case AddToFront(request) => queueRequestInFront(sender, request)
    case RequestCompleted() => handleNextRequest
  }
  
  private def submitRequest(requestor: ActorRef, request: Request): Unit = { 
    request.execute(client, new ResultHandler(requestor))
    inFlightRequests += 1
  }
  
  private def queueRequest(requestor: ActorRef, url: Request): Unit = queuedRequests = queuedRequests :+ (sender, url)
  
  private def queueRequestInFront(requestor: ActorRef, url: Request): Unit = queuedRequests = (requestor, url) +: queuedRequests
  
  private def handleNextRequest: Unit = {
    inFlightRequests -= 1
    queuedRequests.headOption.map { r =>
      submitRequest(r._1, r._2)
      queuedRequests = queuedRequests.tail
    }
  }
}