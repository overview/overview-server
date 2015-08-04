package com.overviewdocs.http

import java.util.concurrent.{ Future => JFuture }

import com.ning.http.client.AsyncCompletionHandler

/** Information needed for Basic Authentication **/
case class Credentials(username: String, password: String)

/** The information we need from an http request */
trait SimpleResponse {
  def status: Int

  /** Returns response body as bytes.
    *
    * We don't provide it as a String because in our case, that value is always
    * wrong. At time of writing, we only request from DocumentCloud, and it
    * returns the wrong encoding. See:
    *
    * * https://www.pivotaltracker.com/story/show/85536256
    * * https://github.com/documentcloud/documentcloud/pull/143
    * * https://github.com/documentcloud/documentcloud/issues/221
    *
    * Once DocumentCloud fixes this, we can change `bodyAsBytes` to `body`, a
    * String. But it might be a long time.
    */
  def bodyAsBytes: Array[Byte]
  
  /** Apparently, headers can have multiple value for a given key */
  def headers(name: String): Seq[String]
  
  /**
   * @return headers in a nicely formatted string: `name1:value1\r\nname2:value2\r\n`
   */
  def headersToString: String
}


/**
 * Simple interface for http GET requests
 */
trait Client {
  /** 
   * Submit a GET request for a public url
   * @param responseHandler is called when request is complete.
   */
  def submit(url: String, responseHandler: AsyncCompletionHandler[Unit]): JFuture[Unit]
  
  /**
   * Submit a GET request with Basic Authentication
   * @param followRedirects should be set to `false` if redirect responses should not be automatically followed.
   * @param responseHandler is called when request is complete.
   */
  def submitWithAuthentication(url: String, credentials: Credentials, followRedirects: Boolean, responseHandler: AsyncCompletionHandler[Unit]): JFuture[Unit]
  
  /** 
   *  Close any connections and release all resources.
   *  Calling any other methods on Client after shutdown results in undefined behavior.
   */
  def shutdown(): Unit
}

