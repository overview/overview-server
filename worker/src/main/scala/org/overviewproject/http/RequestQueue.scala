package org.overviewproject.http

import scala.collection.JavaConverters._

import akka.actor._
import com.ning.http.client.AsyncCompletionHandler
import com.ning.http.client.Response

object RequestQueueProtocol {
  case class AddToEnd(url: String)
  case class Result(response: Response2)
}

class AsyncHttpClientResponse(response: Response) extends Response2 {
  override def status: Int = response.getStatusCode
  override def body: String = response.getResponseBody
  override def headers(name: String): Seq[String] = response.getHeaders(name).asScala.toSeq
}

class RequestQueue(client: Client, maxInFlightRequests: Int) extends Actor {
  import RequestQueueProtocol._
  private case class RequestCompleted()

  private var inFlightRequests: Int = 0
  private var queuedRequests: Seq[(ActorRef, String)] = Seq.empty // FIXME: mutable seq will be more efficient with many requests

  class ResultHandler(requestor: ActorRef) extends AsyncCompletionHandler[Unit] {
    override def onCompleted(response: Response): Unit = {
      requestor ! Result(new AsyncHttpClientResponse(response))
      self ! RequestCompleted()
    }
  }

  def receive = {
    case AddToEnd(url) if inFlightRequests < maxInFlightRequests => submitRequest(sender, url)
    case AddToEnd(url) => queueRequest(url)
    case RequestCompleted() => handleNextRequest
  }
  
  private def submitRequest(requestor: ActorRef, url: String): Unit = { 
    client.submit(url, new ResultHandler(requestor))
    inFlightRequests += 1
  }
  
  private def queueRequest(url: String): Unit = {
    queuedRequests = queuedRequests :+ (sender, url)
  }
  
  private def handleNextRequest: Unit = {
    inFlightRequests -= 1
    queuedRequests.headOption.map { r =>
      submitRequest(r._1, r._2)
      queuedRequests = queuedRequests.tail
    }
  }
}