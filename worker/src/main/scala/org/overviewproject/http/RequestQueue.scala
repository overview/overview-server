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
  private var queuedRequests: Seq[(ActorRef, String)] = Seq.empty

  class ResultHandler(requestor: ActorRef) extends AsyncCompletionHandler[Unit] {
    override def onCompleted(response: Response): Unit = {
      requestor ! Result(new AsyncHttpClientResponse(response))
      self ! RequestCompleted()
    }
  }

  def receive = {
    case AddToEnd(url) if (inFlightRequests < maxInFlightRequests) => { 
      client.submit(url, new ResultHandler(sender))
      inFlightRequests += 1
    }
    case AddToEnd(url) => queuedRequests = queuedRequests :+ (sender, url)
    case RequestCompleted() => {
      inFlightRequests -= 1
      queuedRequests.headOption.map { r =>
        client.submit(r._2, new ResultHandler(r._1))
        inFlightRequests += 1
        queuedRequests = queuedRequests.tail
      }
    }
  }
}