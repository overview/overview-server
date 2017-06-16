package models.export.rows

import akka.stream.scaladsl.Source

/** Specifies the (unformatted) data to export. */
case class Rows(
  /** Header columns. In a CSV, this would correspond to the first row. */
  headers: Array[String],

  /** Rows of data. */
  rows: Source[Array[String], akka.NotUsed]
)
