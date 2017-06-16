package models.export.format

import akka.stream.scaladsl.Source
import akka.util.ByteString

import models.export.rows.Rows

trait Format {
  /** The Content-Type header for this file. */
  val contentType: String

  def byteSource(rows: Rows): Source[ByteString, akka.NotUsed]
}
