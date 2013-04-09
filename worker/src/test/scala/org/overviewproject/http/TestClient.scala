package org.overviewproject.http

import com.ning.http.client.{ AsyncCompletionHandler, Response }

class TestClient extends Client {
  private var requests: Seq[(String, AsyncCompletionHandler[Unit])] = Seq()
  
  def submit(url: String, responseHandler: AsyncCompletionHandler[Unit]): Unit = requests = requests :+ (url, responseHandler)
    
  def submitWithAuthentication(url: String, credentials: Credentials, responseHandler: AsyncCompletionHandler[Unit]): Unit = submit(url, responseHandler) 

  def completeAllRequests(response: Response): Unit = requests.map(_._2.onCompleted(response)) 
  def completeRequest(n: Int, response: Response): Unit = requests(n)._2.onCompleted(response)
  def requestsInFlight: Int = requests.size
}

case class TestResponse2(status: Int, body: String, private val responseHeaders: Map[String, String] = Map()) extends Response2 {
  def headers(name: String): Seq[String] = responseHeaders.get(name) match {
    case Some(s) => Seq(s)
    case None => Seq.empty
  }
  
}