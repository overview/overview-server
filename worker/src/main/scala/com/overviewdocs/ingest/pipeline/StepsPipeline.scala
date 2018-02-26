package com.overviewdocs.ingest.pipeline

import akka.stream.scaladsl.Source
import scala.concurrent.ExecutionContext

import com.overviewdocs.ingest.models.{WrittenFile2,ProcessedFile2}

/** A Pipeline that operates from a series of Steps.
  *
  * For instance, a ".doc" pipeline could be `[ "doc2pdf", "thumbnailer" ]`.
  */
class StepsPipeline(steps: Vector[Step]) extends Pipeline {
  override def process(file2: WrittenFile2)(implicit ec: ExecutionContext): Source[ProcessedFile2, akka.NotUsed] = ???
}
