package org.overviewproject.jobhandler.filegroup.task

import scala.collection.SeqView
import java.awt.image.BufferedImage

trait PdfDocument {
  /** @returns a View to prevent all page data to be loaded into memory at once */
  def pages: SeqView[PdfPage, Seq[_]]
  
  /** @returns a View of images of all the document pages */
  def pageImages: SeqView[BufferedImage, Seq[_]]
  
  /** @returns any found text in the document */
  def text: String
  
  /**
   * @returns `Right(text)` if the text found has associated fonts
   * @returns `Left(text)` if no fonts were found when extracting text
   */
  def textWithFonts: Either[String, String]
  
  def close(): Unit
}

case class PdfPage(data: Array[Byte], text: String)
