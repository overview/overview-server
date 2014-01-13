package models.export.format

import java.io.{BufferedWriter,OutputStream,OutputStreamWriter}

import views.odf.OdfFile
import models.odf.{OdfManifest,OdsSpreadsheet}
import models.export.rows.Rows

object OdsFormat extends Format {
  override val contentType = "application/vnd.oasis.opendocument.spreadsheet"

  override def writeContentsToOutputStream(rows: Rows, outputStream: OutputStream) = {
    val sheet = OdsSpreadsheet(rows.headers, rows.rows)
    val manifest = OdfManifest(Seq(sheet))
    val file = OdfFile(manifest)
    file.writeTo(outputStream)
  }
}
