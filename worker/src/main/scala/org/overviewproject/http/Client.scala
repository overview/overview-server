package org.overviewproject.http

import com.ning.http.client.AsyncCompletionHandler

case class Credentials(userName: String, password: String)

trait SimpleResponse {
  def status: Int
  def headers(name: String): Seq[String]
  def body: String
}



trait Client {
  def submit(url: String, responseHandler: AsyncCompletionHandler[Unit]): Unit
  def submitWithAuthentication(url: String, credentials: Credentials, responseHandler: AsyncCompletionHandler[Unit]): Unit
}

