package com.overviewdocs.ingest.pipeline.logic

import akka.stream.Materializer
import akka.stream.scaladsl.Source
import scala.concurrent.ExecutionContext

import com.overviewdocs.blobstorage.BlobStorage
import com.overviewdocs.ingest.models.WrittenFile2
import com.overviewdocs.ingest.pipeline.{StepLogic,StepOutputFragment}

/** Declares that we have no handler for this file.
  */
class UnhandledStepLogic extends StepLogic {
  override def toChildFragments(
    blobStorage: BlobStorage,
    input: WrittenFile2
  )(implicit ec: ExecutionContext, mat: Materializer): Source[StepOutputFragment, akka.NotUsed] = {
    Source.single(StepOutputFragment.FileError("unhandled"))
  }
}
