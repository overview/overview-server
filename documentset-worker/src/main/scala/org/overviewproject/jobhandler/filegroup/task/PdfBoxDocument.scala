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
import org.apache.pdfbox.io.RandomAccessFile
import java.io.File
import scala.util.control.Exception.ultimately

class PdfBoxDocument(location: String) extends PdfDocument {
  private val TempFilePrefix = "overview-pdfbox-"
  private val TempFileExtension = ".tmp"
  
  private val document: PDDocument = loadFromLocation(location)

  private val textStripper: PDFTextStripper = new PDFTextStripper
  
  override def pages: SeqView[PdfPage, Seq[_]] = splitPages.map { p =>
    val data = getData(p)
    val text = getText(p)

    p.close

    PdfPage(data, text)
  }

  override def text: String = getText(document)

  override def close(): Unit = document.close()
  

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
      BlobStorage.withBlobInTempFile(location)(loadFromFile),
      scala.concurrent.duration.Duration.Inf)
  }

  private def loadFromFile(file: File): Future[PDDocument] = Future.successful {
    val tempFile = File.createTempFile(TempFilePrefix, TempFileExtension)
    val scratchFile = new RandomAccessFile(tempFile, "rw")
    
    ultimately(tempFile.delete) {
      PDDocument.loadNonSeq(file, scratchFile)
    }
  }
  
  private def getData(page: PDDocument): Array[Byte] = {
    val outputStream = new ByteArrayOutputStream()
    page.save(outputStream)
    outputStream.close()

    outputStream.toByteArray()
  }

  private def getText(document: PDDocument): String = {
    val rawText: String = textStripper.getText(document)

    Textify(rawText)
  }
}
