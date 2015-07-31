package org.overviewproject.jobhandler.filegroup.task

import scala.collection.SeqView
import java.awt.image.BufferedImage

trait PdfDocument {
  /** @returns a View to prevent all page data to be loaded into memory at once */
  def pages: Seq[PdfPage]
  
  /** @returns any found text in the document */
  def text: String
  
  /** A [[PdfDocument]] must be closed when it is no longer needed. */
  def close(): Unit
}

trait PdfPage {
  def data: Array[Byte]
  def text: String
  
  /**
   * @returns `Right(text)` if the text found has associated fonts
   * @returns `Left(text)` if no fonts were found when extracting text
   */
  def textWithFonts: Either[String, String]

  def image: BufferedImage
  def close(): Unit
}

