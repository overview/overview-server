package org.overviewproject.http

import com.ning.http.client.AsyncCompletionHandler

case class Credentials(userName: String, password: String)

trait SimpleResponse {
  def status: Int
  def body: String
  def headers(name: String): Seq[String]
  def headersToString: String
}



trait Client {
  def submit(url: String, responseHandler: AsyncCompletionHandler[Unit]): Unit
  def submitWithAuthentication(url: String, credentials: Credentials, followRedirects: Boolean, responseHandler: AsyncCompletionHandler[Unit]): Unit
}

