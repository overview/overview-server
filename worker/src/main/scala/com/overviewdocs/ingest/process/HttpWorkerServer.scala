package com.overviewdocs.ingest.process

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.{Http,HttpExt}
import akka.http.scaladsl.server.{Route,RoutingLog}
import akka.http.scaladsl.server.directives.DebuggingDirectives
import akka.http.scaladsl.settings.{ParserSettings,RoutingSettings}
import akka.stream.Materializer
import scala.concurrent.Future

/** Creates an HTTP server that manages workers and tasks.
  *
  * An "Ocr" worker will do this, on repeat:
  *
  * 1. POST /Ocr => expected 201 Created with JSON
  *    `{ id, filename, contentType, languageCode, blobUrl, metadata, wantOcr, wantSplitByPage }`
  *    (the server will pause a few seconds before returning 204 No Content if
  *    there is no task to complete; so workers can request on a loop until a
  *    non-empty response.
  * 2. GET /Ocr/:id/blob => 200 streaming bytes from BlobStorage. (If blob
  *    location is S3, `blobUrl` will be a pre-signed URL, not this one, so the
  *    worker will never hit this path.)
  * 3. PUT /Ocr/:id/0.json with
  *    `{ id, filename, contentType, languageCode, metadata, wantOcr, wantSplitByPage }`
  *    => 202 Accepted starts creating a child
  * 4. PUT /Ocr/:id/0.blob, streaming bytes to BlobStorage => 202 Accepted child
  *    bytes (now the child is valid)
  * 5. PUT /Ocr/:id/progress with Number in the range `[ 0 .. 1.0 ]`
  *    => 202 Accepted
  * 6. PUT /Ocr/:id/error with UTF-8 error message => 202 Accepted and the task
  *    disappears
  *    -or-
  *    PUT /Ocr/:id/done, empty => 202 Accepted and the task disappears
  * 7. HEAD /Ocr/:id or GET /Ocr/:id => returns 404 Not Found if the task is
  *    canceled, timed out, or otherwise finished. Workers should abort a task
  *    quickly if they see 404 Not Found, because everything they post is being
  *    ignored.
  *
  * Create the server like this:
  *
  * {{{
  * val server = HttpWorkerServer("0.0.0.0", 9032)
  * val route = materializeAGraphWithHttpStepHandlerRoutesConcatenated
  * val futureHttpBinding = server.bindAndHandle(route)
  * def shutdown = for {
  *   httpBinding <- futureHttpBinding
  *   _ <- httpBinding.unbind
  * } yield akka.Done
  * }}}
  */
class HttpWorkerServer(
  actorSystem: ActorSystem,
  routingSettings: RoutingSettings,
  parserSettings: ParserSettings,
  routingLog: RoutingLog,
  interface: String,
  port: Int
) {
  def bindAndHandle(
    route: Route
  )(implicit mat: Materializer): Future[Http.ServerBinding] = {
    val httpExt = Http(actorSystem)
    implicit val rs = routingSettings
    implicit val ps = parserSettings
    implicit val rl = routingLog
    val logging = DebuggingDirectives.logRequestResult("http-convert", Logging.InfoLevel)
    val sealedRoute = Route.seal(logging { route })
    val handler = Route.asyncHandler(sealedRoute)
    httpExt.bindAndHandleAsync(handler, interface, port)
  }
}

object HttpWorkerServer {
  def apply(
    actorSystem: ActorSystem,
    interface: String,
    port: Int
  ): HttpWorkerServer = {
    val routingSettings = RoutingSettings(actorSystem)
    val parserSettings = ParserSettings(actorSystem)
    val routingLog = RoutingLog.fromActorSystem(actorSystem)

    new HttpWorkerServer(
      actorSystem,
      routingSettings,
      parserSettings,
      routingLog,
      interface,
      port
    )
  }
}
