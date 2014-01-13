package models.export.format

import java.io.{BufferedWriter,OutputStream,OutputStreamWriter}

import views.ooxml.XlsxFile
import models.export.rows.Rows

object XlsxFormat extends Format {
  override val contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

  override def writeContentsToOutputStream(rows: Rows, outputStream: OutputStream) = {
    val file = XlsxFile(rows)
    file.writeTo(outputStream)
  }
}
