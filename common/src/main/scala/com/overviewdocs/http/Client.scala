package com.overviewdocs.http

import scala.concurrent.{ExecutionContext,Future}

/**
 * Simple interface for http GET requests
 */
trait Client {
  /** 
   * Submit a GET request for a public url
   *
   * @param url URL to get
   * @return The response, or a failure if we didn't get one
   */
  def get(url: String)(implicit ec: ExecutionContext): Future[Response]

  /**
    * Submit a GET request with Basic Authentication
    *
    * @param url URL to get
    * @param credentials User credentials
    * @param followRedirects If `false`, don't follow redirects
    */
  def get(url: String, credentials: Credentials, followRedirects: Boolean = true)(implicit ec: ExecutionContext): Future[Response]

  /**
    * Submit a GET request, with or without Basic Authentication
    *
    * @param url URL to get
    * @param maybeCredentials Credentials, if set
    */
  def get(url: String, maybeCredentials: Option[Credentials])(implicit ec: ExecutionContext): Future[Response] = {
    maybeCredentials match {
      case None => get(url)
      case Some(credentials) => get(url, credentials)
    }
  }

  /** 
   *  Close any connections and release all resources.
   *  Calling any other methods on Client after shutdown results in undefined behavior.
   */
  def shutdown: Unit
}
