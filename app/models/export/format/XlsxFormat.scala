package models.export.format

import java.io.OutputStream
import scala.concurrent.Future

import views.ooxml.XlsxFile
import models.export.rows.Rows

object XlsxFormat extends Format {
  override val contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

  override def writeContentsToOutputStream(rows: Rows, outputStream: OutputStream): Future[Unit] = {
    val file = XlsxFile(rows)
    file.writeTo(outputStream)
  }
}
