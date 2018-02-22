package com.overviewdocs.ingest.pipeline

import akka.stream.scaladsl.Source
import scala.concurrent.{ExecutionContext,Future}

import com.overviewdocs.ingest.File2Writer
import com.overviewdocs.models.File2

/** Converts a File2 from WRITTEN to PROCESSED and outputs its WRITTEN
  * children.
  */
class Step(logic: StepLogic, file2Writer: File2Writer) {
  /** Converts a File2 from WRITTEN to PROCESSED and outputs its WRITTEN
    * children. (Just the children! Not itself.)
    *
    * The return value is the input File2 as written to the database. It may
    * have a `processingError`.
    */
  def process(file2: File2)(implicit ec: ExecutionContext): Source[File2, Future[File2]] = ???
}
