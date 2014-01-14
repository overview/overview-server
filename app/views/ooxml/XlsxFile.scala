package views.ooxml

import java.io.OutputStream
import java.util.zip.{ZipEntry,ZipOutputStream}

import models.export.rows.Rows
import views.xml.ooxml.XlsxSpreadsheetContent

case class XlsxFile(spreadsheet: Rows) {
  // http://blogs.msdn.com/b/brian_jones/archive/2006/11/02/simple-spreadsheetml-file-part-1-of-3.aspx

  def writeTo(out: OutputStream) : Unit = {
    val zipStream = new ZipOutputStream(out)

    writeRelsTo(zipStream)
    writeContentTypesTo(zipStream)
    writeXlRelTo(zipStream)
    writeWorkbookTo(zipStream)
    writeWorksheetTo(zipStream)

    zipStream.finish()
  }

  private def writeStringContent(filename: String, zipStream: ZipOutputStream, content: String) : Unit = {
    zipStream.putNextEntry(new ZipEntry(filename))
    zipStream.write(content.getBytes("utf-8"))
    zipStream.closeEntry()
  }

  private def writeRelsTo(zipStream: ZipOutputStream) : Unit = {
    writeStringContent("_rels/.rels", zipStream, """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
      |<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
      |  <Relationship Id="rId1"
      |                Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument"
      |                Target="xl/workbook.xml"/>
      |</Relationships>""".stripMargin)
  }

  private def writeContentTypesTo(zipStream: ZipOutputStream) : Unit = {
    writeStringContent("[Content_Types].xml", zipStream, """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
      |<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
      |  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
      |  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
      |  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
      |</Types>""".stripMargin)
  }

  private def writeXlRelTo(zipStream: ZipOutputStream) : Unit = {
    writeStringContent("xl/_rels/workbook.xml.rels", zipStream, """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
      |<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
      |  <Relationship Id="rId1"
      |                Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet"
      |                Target="worksheets/sheet1.xml"/>
      |</Relationships>""".stripMargin)
  }

  private def writeWorkbookTo(zipStream: ZipOutputStream) : Unit = {
    writeStringContent("xl/workbook.xml", zipStream, """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
      |<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
      |          xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
      |  <sheets>
      |    <sheet name="sheet1" sheetId="1" r:id="rId1"/>
      |  </sheets>
      |</workbook>""".stripMargin)
  }

  private def writeWorksheetTo(zipStream: ZipOutputStream) : Unit = {
    val xml = XlsxSpreadsheetContent(spreadsheet)
    writeStringContent("xl/worksheets/sheet1.xml", zipStream, xml.toString)
  }
}
