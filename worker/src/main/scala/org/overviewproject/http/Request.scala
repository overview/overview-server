package org.overviewproject.http

import com.ning.http.client.AsyncCompletionHandler

trait Request {
  val url: String
  
  def execute(client: Client, responseHandler: AsyncCompletionHandler[Unit]): Unit
  def cancel
}

import java.util.concurrent.{ Future => JFuture }
abstract class CancellableRequest extends Request {
  protected var requestFuture: JFuture[Unit] = _
  
  def cancel = requestFuture.cancel(true) 
}

case class PublicRequest(url: String) extends CancellableRequest {
  override def execute(client: Client, responseHandler: AsyncCompletionHandler[Unit]): Unit = 
    requestFuture = client.submit(url, responseHandler)
  
}

case class PrivateRequest(url: String, credentials: Credentials, redirect: Boolean = true) extends CancellableRequest {
  override def execute(client: Client, responseHandler: AsyncCompletionHandler[Unit]): Unit = 
    requestFuture = client.submitWithAuthentication(url, credentials, redirect, responseHandler)
}