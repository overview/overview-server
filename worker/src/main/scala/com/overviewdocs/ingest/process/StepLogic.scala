package com.overviewdocs.ingest.process

import akka.stream.Materializer
import akka.stream.scaladsl.Source
import scala.concurrent.{ExecutionContext,Future}

import com.overviewdocs.blobstorage.BlobStorage
import com.overviewdocs.ingest.model.WrittenFile2
import com.overviewdocs.models.File2

/** Produces StepOutputFragments from a File2.
  *
  * Step does the actual database writes. StepLogic provides the API for
  * conversion implementations.
  */
trait StepLogic {
  val id: String

  val progressWeight: Double

  /** Streams the data needed to convert a WRITTEN File2 to a PROCESSED one. */
  def toChildFragments(
    blobStorage: BlobStorage,
    file2: WrittenFile2
  )(implicit mat: Materializer): Source[StepOutputFragment, akka.NotUsed]
}
