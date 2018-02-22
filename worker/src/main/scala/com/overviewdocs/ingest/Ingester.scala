package com.overviewdocs.ingest

import akka.stream.scaladsl.Source

import scala.concurrent.{ExecutionContext,Future}

import com.overviewdocs.models.File2

class Ingester(file2Writer: File2Writer) {
  /** Converts a File2 into one or more File2 instances.
    *
    * This will PROCESS the File2 using the given Pipeline, ensuring all
    * Documents and File2Errors are written and marking INGESTED as it
    * goes.
    *
    * Any INGESTED File2 has had all its Documents and/or File2Errors written.
    * Ingesting an INGESTED File2 is a no-op.
    */
  def ingestFile2(
    file2: File2,
    onProgress: Double => Boolean
  )(implicit ec: ExecutionContext): Future[Unit] = ???
  // TODO test ingesting INGESTED file
}
