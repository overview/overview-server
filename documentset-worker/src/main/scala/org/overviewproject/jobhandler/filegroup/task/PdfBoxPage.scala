package org.overviewproject.jobhandler.filegroup.task

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream

import scala.collection.JavaConverters._

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.util.PDFTextStripper
import org.apache.pdfbox.util.TextPosition
import org.overviewproject.util.Textify

trait PdfBoxPage extends PdfPage {

  private val ImageType = BufferedImage.TYPE_BYTE_GRAY
  private val ImageResolution = 400

  protected val document: PDDocument
  protected val textStripper: PdfBoxPage.FontDetectingTextStripper

  override def data: Array[Byte] = {
    val outputStream = new ByteArrayOutputStream()
    document.save(outputStream)
    outputStream.close()

    outputStream.toByteArray()
  }

  override def text: String = {
    val rawText: String = textStripper.getText(document)

    Textify(rawText)
  }

  /**
   * @returns a rendering of the page as an image.
   * @throws an exception if no page is found, because a [[PdfBoxPage]] must contain
   * one (and only one) page.
   */
  override def image: BufferedImage = {
    val page = document.getDocumentCatalog.getAllPages.asScala.head.asInstanceOf[PDPage]

    page.convertToImage(ImageType, ImageResolution)
  }

  /**
   * Closes the underlying document.
   * The [[PdfPage]] must be closed when it is no longer used.
   * Calling methods on a closed [[PdfPage]] leads to undefined behavior
   */
  override def close(): Unit = document.close()

}

object PdfBoxPage {
  def apply(document: PDDocument): PdfBoxPage = new PdfBoxPageImpl(document)

  private class PdfBoxPageImpl(override protected val document: PDDocument) extends PdfBoxPage {
    override protected val textStripper = new FontDetectingTextStripper
  }

  class FontDetectingTextStripper extends PDFTextStripper {
    import org.apache.pdfbox.util.TextPosition

    private var detectedFont: Boolean = false

    def foundFonts: Boolean = detectedFont

    override protected def writeString(text: String, textPositions: java.util.List[TextPosition]): Unit = {
      super.writeString(text, textPositions)

      if (!detectedFont) {
        val fontNames = textPositions.asScala.map(_.getFont().getBaseFont())
        if (fontNames.nonEmpty) detectedFont = true
      }
    }
  }

}