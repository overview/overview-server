package models.export

import java.io.FileInputStream
import scala.concurrent.Future

import models.export.rows.Rows
import models.export.format.Format

/** Something that will end up as a file on the user's computer. */
class Export(rows: Rows, format: Format) {
  /** Content-Type header, as per HTTP.
    *
    * For instance: text/csv; charset="utf-8"
    */
  def contentType: String = format.contentType

  /** InputStream of bytes we'll produce.
    *
    * Calls to .read() may block a bit, as they'll grab more rows from the Rows
    * enumerator. (That, in turn, can lead to a database fetch.)
    */
  def futureFileInputStream: Future[FileInputStream] = format.getContentsAsInputStream(rows)
}
