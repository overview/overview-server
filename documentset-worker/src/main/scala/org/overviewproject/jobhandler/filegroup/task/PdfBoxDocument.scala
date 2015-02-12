package org.overviewproject.jobhandler.filegroup.task

import java.io.ByteArrayOutputStream
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.util.PDFTextStripper
import scala.collection.JavaConverters._
import scala.concurrent.Future

import org.overviewproject.blobstorage.BlobStorage
import org.overviewproject.postgres.LargeObjectInputStream
import org.overviewproject.util.Textify

class PdfBoxDocument(location: String) extends PdfDocument {

  private val document: PDDocument = loadFromLocation(location)
  private val documentPages: Iterable[PDDocument] = splitPages
  private val textStripper: PDFTextStripper = new PDFTextStripper

  override def pages: Iterable[PdfPage] = documentPages.map { p =>
    val data = getData(p)
    val text = getText(p)
     
    p.close
    
    PdfPage(data, text)
  }

  override def text: String = textStripper.getText(document)

  override def close(): Unit = {
    documentPages.foreach(_.close())
    document.close()
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


  private def loadFromLocation(location: String): PDDocument = {
    scala.concurrent.Await.result(
      BlobStorage.withBlobInTempFile(location)(file => Future.successful(PDDocument.load(file))),
      scala.concurrent.duration.Duration.Inf
    )
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
