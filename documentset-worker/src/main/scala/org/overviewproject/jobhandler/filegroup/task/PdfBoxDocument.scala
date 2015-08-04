package com.overviewdocs.jobhandler.filegroup.task

import java.io.ByteArrayOutputStream
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.util.PDFTextStripper
import scala.collection.JavaConverters._
import scala.collection.mutable.Buffer
import scala.concurrent.Future
import com.overviewdocs.blobstorage.BlobStorage
import com.overviewdocs.util.Textify
import scala.collection.IterableView
import scala.collection.SeqView
import org.apache.pdfbox.io.RandomAccessFile
import java.io.File
import scala.util.control.Exception.ultimately
import scala.util.Try
import java.awt.image.BufferedImage

class PdfBoxDocument(file: File) extends PdfDocument {
  private val TempFilePrefix = "overview-pdfbox-"
  private val TempFileExtension = ".tmp"


  private val textStripper = new PDFTextStripper
  private val document: PDDocument = init(file)

  override def pages: Seq[PdfPage] = splitPages.map(PdfBoxPage(_))

  override def text: String = getText(document)

  override def close(): Unit = document.close()

  private def init(file: File): PDDocument = {
    val tempFile = File.createTempFile(TempFilePrefix, TempFileExtension)
    val scratchFile = new RandomAccessFile(tempFile, "rw")

    ultimately(tempFile.delete) {
      PDDocument.loadNonSeq(file, scratchFile)
    }
  }

  private def getPages: Seq[PDPage] =
    document.getDocumentCatalog.getAllPages.asScala.map(_.asInstanceOf[PDPage])

  private def splitPages: Seq[PDDocument] =
    getPages.map(createDocument)

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

object PdfBoxDocument {
  def loadFromLocation(location: String): Future[PdfDocument] =
    BlobStorage.withBlobInTempFile(location)(loadFromFile)

  def loadFromFile(file: File): Future[PdfDocument] = Future.fromTry(
    Try(new PdfBoxDocument(file)))

}