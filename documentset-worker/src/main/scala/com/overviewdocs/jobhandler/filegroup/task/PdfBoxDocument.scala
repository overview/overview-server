package com.overviewdocs.jobhandler.filegroup.task

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import org.apache.pdfbox.io.RandomAccessFile
import org.apache.pdfbox.pdmodel.{PDDocument,PDPage}
import org.apache.pdfbox.util.PDFTextStripper
import scala.collection.JavaConverters.iterableAsScalaIterableConverter
import scala.concurrent.Future
import scala.util.control.Exception.ultimately

import com.overviewdocs.blobstorage.BlobStorage
import com.overviewdocs.util.Textify

class PdfBoxDocument(file: File) extends PdfDocument {
  private val TempFilePrefix = "overview-pdfbox-"
  private val TempFileExtension = ".tmp"


  private val textStripper = new PDFTextStripper
  private val document: PDDocument = init(file)

  override def pages: Iterable[PdfPage] = splitPages.map(PdfBoxPage(_))

  override def text: String = getText(document)

  override def close(): Unit = document.close()

  private def init(file: File): PDDocument = {
    val tempFile = File.createTempFile(TempFilePrefix, TempFileExtension)
    val scratchFile = new RandomAccessFile(tempFile, "rw")

    ultimately(tempFile.delete) {
      PDDocument.loadNonSeq(file, scratchFile)
    }
  }

  private def getPages: Iterable[PDPage] = {
    val jPages = document.getDocumentCatalog.getAllPages
    jPages.asScala.map(_.asInstanceOf[PDPage])
  }

  private def splitPages: Iterable[PDDocument] = getPages.map(createDocument)

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
  def loadFromLocation(location: String): Future[PdfDocument] = {
    BlobStorage.withBlobInTempFile(location)(loadFromFile)
  }

  /** DO NOT USE -- it's synchronous.
    *
    * To make it async, we'd need to add an ExecutionContext implicit parameter. Boo.
    */
  def loadFromFile(file: File): Future[PdfDocument] = Future.successful(new PdfBoxDocument(file))
}
