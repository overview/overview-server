package org.overviewproject.http

import scala.collection.JavaConverters._

import akka.actor._
import com.ning.http.client.AsyncCompletionHandler
import com.ning.http.client.Response

object RequestQueueProtocol {
  case class AddToEnd(url: Request)
  case class AddToFront(url: Request)
  case class Result(response: SimpleResponse)
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
  }

  def receive = {
    case AddToEnd(url) if inFlightRequests < maxInFlightRequests => submitRequest(sender, url)
    case AddToEnd(url) => queueRequest(sender, url)
    case AddToFront(url) if inFlightRequests < maxInFlightRequests => submitRequest(sender, url)
    case AddToFront(url) => queueRequestInFront(sender, url)
    case RequestCompleted() => handleNextRequest
  }
  
  private def submitRequest(requestor: ActorRef, url: Request): Unit = { 
    client.submit(url.url, new ResultHandler(requestor))
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