package views.ooxml

import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.{ZipEntry,ZipOutputStream}
import scala.concurrent.{ExecutionContext,Future,blocking}

class XlsxFile(private val sink: OutputStream)
{
  private val zipStream = new ZipOutputStream(sink)
  private val charset = StandardCharsets.UTF_8

  // http://blogs.msdn.com/b/brian_jones/archive/2006/11/02/simple-spreadsheetml-file-part-1-of-3.aspx

  def writeBegin: Unit = {
    writeRels
    writeContentTypes
    writeXlRel
    writeWorkbook

    zipStream.putNextEntry(new ZipEntry("xl/worksheets/sheet1.xml"))
    zipStream.write(XlsxSpreadsheetContent.header.getBytes(charset))
  }

  def writeHeaders(headers: Array[String]): Unit = writeRow(headers)

  def writeRow(row: Array[String]): Unit = {
    val string = XlsxSpreadsheetContent.row(row)
    zipStream.write(string.getBytes(charset))
  }

  def writeEnd: Unit = {
    zipStream.write(XlsxSpreadsheetContent.footer.getBytes(charset))
    zipStream.closeEntry
    zipStream.finish
    zipStream.close
  }

  private def writeStringContent(filename: String, content: String) : Unit = {
    zipStream.putNextEntry(new ZipEntry(filename))
    zipStream.write(content.getBytes(charset))
    zipStream.closeEntry()
  }

  private def writeRels: Unit = {
    writeStringContent("_rels/.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
      |<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
      |  <Relationship Id="rId1"
      |                Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument"
      |                Target="xl/workbook.xml"/>
      |</Relationships>""".stripMargin)
  }

  private def writeContentTypes: Unit = {
    writeStringContent("[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
      |<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
      |  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
      |  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
      |  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
      |</Types>""".stripMargin)
  }

  private def writeXlRel: Unit = {
    writeStringContent("xl/_rels/workbook.xml.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
      |<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
      |  <Relationship Id="rId1"
      |                Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet"
      |                Target="worksheets/sheet1.xml"/>
      |</Relationships>""".stripMargin)
  }

  private def writeWorkbook: Unit = {
    writeStringContent("xl/workbook.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
      |<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
      |          xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
      |  <sheets>
      |    <sheet name="sheet1" sheetId="1" r:id="rId1"/>
      |  </sheets>
      |</workbook>""".stripMargin)
  }
}
