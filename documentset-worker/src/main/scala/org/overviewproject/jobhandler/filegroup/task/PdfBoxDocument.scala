package org.overviewproject.jobhandler.filegroup.task

import java.io.ByteArrayOutputStream
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.util.PDFTextStripper
import scala.collection.JavaConverters._
import scala.concurrent.Future
import org.overviewproject.blobstorage.BlobStorage
import org.overviewproject.util.Textify
import scala.collection.IterableView
import scala.collection.SeqView

class PdfBoxDocument(location: String) extends PdfDocument {

  private val document: PDDocument = loadFromLocation(location)
  private val documentPages = splitPages
  private val textStripper: PDFTextStripper = new PDFTextStripper

  override def pages: SeqView[PdfPage, Seq[_]] = documentPages.map { p =>
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

  // Use a view to prevent all page data from being loaded into memory at once
  private def splitPages: SeqView[PDDocument, Seq[_]] =
    document.getDocumentCatalog.getAllPages().asScala.view.map { p =>
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
      scala.concurrent.duration.Duration.Inf)
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
