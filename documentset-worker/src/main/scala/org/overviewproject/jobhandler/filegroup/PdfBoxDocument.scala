package org.overviewproject.jobhandler.filegroup

import scala.collection.JavaConverters._
import org.apache.pdfbox.pdmodel.PDDocument
import org.overviewproject.postgres.LargeObjectInputStream
import org.apache.pdfbox.util.Splitter
import org.apache.pdfbox.util.PDFTextStripper
import java.io.ByteArrayOutputStream
import org.overviewproject.util.Textify
import org.apache.pdfbox.pdmodel.PDPage

class PdfBoxDocument(oid: Long) extends PdfDocument {

  private val document: PDDocument = loadFromOid(oid)
  private val documentPages: Iterable[PDDocument] = splitPages
  private val textStripper: PDFTextStripper = new PDFTextStripper

  def pages: Iterable[PdfPage] = documentPages.map { p =>
    val data = getData(p)
    val text = getText(p)
     
    p.close
    
    PdfPage(data, text)
  }

  private def splitPages: Iterable[PDDocument] = document.getDocumentCatalog.getAllPages().asScala.view.map { p =>
    createDocument(p.asInstanceOf[PDPage])
  }


  private def createDocument(page: PDPage): PDDocument = {
    
    val pageDocument = new PDDocument()
    pageDocument.setDocumentInformation(document.getDocumentInformation())
    pageDocument.getDocumentCatalog().setViewerPreferences(
      document.getDocumentCatalog().getViewerPreferences())

    val pageInDocument = pageDocument.importPage(page)
    pageInDocument.setCropBox(page.findCropBox());
    pageInDocument.setMediaBox(page.findMediaBox());

    pageInDocument.setResources(page.getResources());
    pageInDocument.setRotation(page.findRotation());

    pageDocument
  }

  def close(): Unit = {
    documentPages.foreach(_.close())
    document.close()
  }

  private def loadFromOid(oid: Long): PDDocument = {
    val documentStream = new LargeObjectInputStream(oid)
    PDDocument.load(documentStream)
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