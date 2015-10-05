package models.export.format

import java.io.OutputStream

import views.ooxml.XlsxFile

object XlsxFormat extends Format with WriteBasedFormat[XlsxFile] {
  override val contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

  override protected def createContext(sink: OutputStream) = new XlsxFile(sink)
  override protected def writeBegin(xlsxFile: XlsxFile) = xlsxFile.writeBegin
  override protected def writeHeaders(headers: Array[String], xlsxFile: XlsxFile) = xlsxFile.writeHeaders(headers)
  override protected def writeRow(row: Array[String], xlsxFile: XlsxFile) = xlsxFile.writeRow(row)
  override protected def writeEnd(xlsxFile: XlsxFile) = xlsxFile.writeEnd
}
