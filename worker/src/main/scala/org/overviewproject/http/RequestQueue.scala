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

class RequestQueue(client: Client) extends Actor {
  import RequestQueueProtocol._

  class ResultHandler(requestor: ActorRef) extends AsyncCompletionHandler[Unit] {
    override def onCompleted(response: Response): Unit = requestor ! Result(new AsyncHttpClientResponse(response))
  }
  
  def receive = {
    case AddToEnd(url) => client.submit(url, new ResultHandler(sender)) 
  }
}