package com.overviewdocs.ingest.process.logic

import akka.stream.Materializer
import akka.stream.scaladsl.Source
import scala.concurrent.ExecutionContext

import com.overviewdocs.blobstorage.BlobStorage
import com.overviewdocs.ingest.model.WrittenFile2
import com.overviewdocs.ingest.process.{StepLogic,StepOutputFragment}

/** Declares that we have no handler for this file.
  */
class UnhandledStepLogic extends StepLogic {
  override val id = "Unhandled"
  override val progressWeight = 1.0

  override def toChildFragments(
    blobStorage: BlobStorage,
    input: WrittenFile2
  )(implicit mat: Materializer): Source[StepOutputFragment, akka.NotUsed] = {
    Source.single(StepOutputFragment.FileError("unhandled"))
  }
}
