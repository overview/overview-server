package org.overviewproject.http

import com.ning.http.client.{ AsyncCompletionHandler, Response }

class TestClient extends Client {
  private var requests: Seq[(String, AsyncCompletionHandler[Unit])] = Seq()
  
  def submit(url: String, responseHandler: AsyncCompletionHandler[Unit]): Unit = requests = requests :+ (url, responseHandler)
    
  def submitWithAuthentication(url: String, credentials: Credentials, followRedirects: Boolean, responseHandler: AsyncCompletionHandler[Unit]): Unit = submit(url, responseHandler) 

  def completeAllRequests(response: Response): Unit = {
    requests.map(_._2.onCompleted(response))
    requests = Seq()
  }
  
  def completeNext(response: Response): Unit = {
    val completeRequest = requests.head
    completeRequest._2.onCompleted(response)
    
    requests = requests.tail
  }
  
  def failNext(t: Throwable): Unit = {
    val failedRequest = requests.head
    failedRequest._2.onThrowable(t)
    
    requests = requests.tail
  }
  
  def requestsInFlight: Int = requests.size
  def requestedUrls: Seq[String] = requests.map(_._1)
}

