package org.overviewproject.jobhandler.filegroup

import scala.collection.JavaConverters._
import org.apache.pdfbox.pdmodel.PDDocument
import org.overviewproject.postgres.LargeObjectInputStream
import org.apache.pdfbox.util.Splitter
import org.apache.pdfbox.util.PDFTextStripper
import java.io.ByteArrayOutputStream
import org.overviewproject.util.Textify

class PdfBoxDocument(oid: Long) extends PdfDocument {

  private val document: PDDocument = loadFromOid(oid)
  private val documentPages: Seq[PDDocument] = splitPages
  private val textStripper: PDFTextStripper = new PDFTextStripper

  def pages: Iterable[PdfPage] = documentPages.map { p =>
    val data = getData(p)
    val text = getText(p)

    PdfPage(data, text)
  }

  def close(): Unit = {
    documentPages.foreach(_.close())
    document.close()
  }

  private def loadFromOid(oid: Long): PDDocument = {
    val documentStream = new LargeObjectInputStream(oid)
    PDDocument.load(documentStream)
  }

  private def splitPages: Seq[PDDocument] = {
    val splitter = new Splitter()
    splitter.setSplitAtPage(1)

    splitter.split(document).asScala
  }

  private def getData(page: PDDocument): Array[Byte] = {
    val outputStream = new ByteArrayOutputStream()
    page.save(outputStream)
    outputStream.close()

    outputStream.toByteArray()
  }

  private def getText(page: PDDocument): String = {
    val rawText: String = textStripper.getText(page)

    Textify(rawText)
  }
}