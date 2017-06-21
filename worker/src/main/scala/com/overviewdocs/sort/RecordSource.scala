package com.overviewdocs.sort

import akka.stream.scaladsl.Source
import scala.concurrent.Future

/** A producer of Records.
  */
case class RecordSource(
  /** Number of records in the Source.
    *
    * This helps calculate progress.
    */
  nRecords: Int,

  /** Records.
    *
    * The materialized value resolves when cleanup has occurred. For instance,
    * if these records are on disk, the Future resolves when the file has been
    * deleted.
    */
  records: Source[Record, Future[Unit]]
)
