package com.overviewdocs.ingest.pipeline

import akka.stream.Materializer
import akka.stream.scaladsl.Source
import scala.concurrent.{ExecutionContext,Future}

import com.overviewdocs.ingest.models.{CreatedFile2,WrittenFile2}
import com.overviewdocs.models.File2

/** Produces StepOutputFragments from a File2.
  *
  * Step does the actual database writes. StepLogic provides the API for
  * conversion implementations.
  */
trait StepLogic {
  /** Streams the data needed to convert a WRITTEN File2 to a PROCESSED one. */
  def toChildFragments(
    file2: WrittenFile2
  )(implicit ec: ExecutionContext, mat: Materializer): Source[StepOutputFragment, akka.NotUsed]
}
