package models.export

import play.api.libs.iteratee.Enumerator

import models.export.rows.Rows
import models.export.format.Format

/** Something that will end up as a file on the user's computer. */
class Export(rows: Rows, format: Format) {
  /** Content-Type header, as per HTTP.
    *
    * For instance: text/csv; charset="utf-8"
    */
  def contentType: String = format.contentType

  /** Generate byte data.
    */
  def bytes: Enumerator[Array[Byte]] = format.bytes(rows)
}
