package com.overviewdocs.ingest.convert

import akka.http.scaladsl.model.{HttpRequest,HttpResponse}
import scala.concurrent.Future

class TaskHandler(
  val task: Task
) {
  def handlePost(request: HttpRequest, heartbeat: () => Unit): Future[HttpResponse] = ???
  def handleGet(request: HttpRequest): Future[HttpResponse] = ???
  def handleHead(request: HttpRequest): Future[HttpResponse] = ???
  def onCancel: Unit = ???
}
