package models.export.format

import play.api.libs.iteratee.Enumerator

import models.export.rows.Rows

trait Format {
  /** The Content-Type header for this file. */
  val contentType: String

  def bytes(rows: Rows): Enumerator[Array[Byte]]
}
