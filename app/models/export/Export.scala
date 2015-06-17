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

  /** FileInputStream that we wish to transfer.
    *
    * This method must be called within an OverviewDatabase.inTransaction
    * block: it may require database access.
    */
  def futureFileInputStream: Future[FileInputStream] = format.getContentsAsInputStream(rows)
}
