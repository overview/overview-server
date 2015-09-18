package com.overviewdocs.http

import scala.concurrent.{ExecutionContext,Future}

/** Sends GET requests.
  */
trait Client {
  /** Send a GET request. */
  def get(request: Request)(implicit ec: ExecutionContext): Future[Response]

  /** Close all open connections.
    *
    * Any `Future`s returned from `get()` will fail with a
    * HttpClientShutDownException.
    */
  def shutdown: Unit
}
