package com.overviewdocs.ingest.pipeline

import akka.stream.scaladsl.Source
import scala.concurrent.ExecutionContext

import com.overviewdocs.models.File2

/** A Pipeline that operates from a series of Steps.
  *
  * For instance, a ".doc" pipeline could be `[ "doc2pdf", "thumbnailer" ]`.
  */
class StepsPipeline(steps: Vector[Step]) extends Pipeline {
  override def processDepthFirst(file2: File2)(implicit ec: ExecutionContext): Source[Pipeline.Output, akka.NotUsed] = ???
}
