package com.overviewdocs.ingest.process.convert

import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import scala.concurrent.ExecutionContext

/** The place Tasks go to be completed.
  *
  * By the time Tasks arrive here, their sinks are wired up to produce the
  * output their sources expect. The goal here is to send all needed
  * StepOutputFragments for the Task and then clean it out of memory.
  *
  * The way to complete Tasks is through HttpRequests. We marry each incoming
  * HttpRequest with a Task (or we error out). All HttpRequests must be handled.
  */
class HttpConvertSink(implicit ec: ExecutionContext, mat: Materializer) {
  val taskSink: Sink[Task, akka.NotUsed] = {
    Sink.ignore.mapMaterializedValue(_ => akka.NotUsed)
  }
}
