package com.overviewdocs.ingest.pipeline

import akka.stream.scaladsl.Source
import scala.concurrent.{ExecutionContext,Future}

import com.overviewdocs.models.File2

/** Produces StepOutputFragments from a File2.
  *
  * Step does the actual database writes. StepLogic provides the API for
  * conversion implementations.
  */
trait StepLogic {
  /** Streams the data needed to convert a WRITTEN File2 to a PROCESSED one.
    */
  def processIntoFragments(file2: File2)(implicit ec: ExecutionContext): Source[StepOutputFragment, Future[StepOutputEnd]]
}
