package org.overviewproject.http

import com.ning.http.client.AsyncCompletionHandler

trait Request {
  def execute(client: Client, responseHandler: AsyncCompletionHandler[Unit]): Unit
}

case class PublicRequest(url: String) extends Request {
  override def execute(client: Client, responseHandler: AsyncCompletionHandler[Unit]): Unit = client.submit(url, responseHandler)
}

case class PrivateRequest(url: String, credentials: Credentials, redirect: Boolean = true) extends Request {
  override def execute(client: Client, responseHandler: AsyncCompletionHandler[Unit]): Unit = client.submitWithAuthentication(url, credentials, redirect, responseHandler)
}