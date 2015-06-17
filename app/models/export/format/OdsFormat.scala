package models.export.format

import java.io.{BufferedWriter,OutputStream,OutputStreamWriter}
import scala.concurrent.Future

import views.odf.OdfFile
import models.export.rows.Rows

object OdsFormat extends Format {
  override val contentType = "application/vnd.oasis.opendocument.spreadsheet"

  override def writeContentsToOutputStream(rows: Rows, outputStream: OutputStream): Future[Unit] = {
    OdfFile(rows).writeTo(outputStream)
  }
}
