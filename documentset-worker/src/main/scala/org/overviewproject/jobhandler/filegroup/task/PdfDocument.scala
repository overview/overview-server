package org.overviewproject.jobhandler.filegroup.task

import scala.collection.SeqView

trait PdfDocument {
  /** @returns a View to prevent all page data to be loaded into memory at once */
  def pages: SeqView[PdfPage, Seq[_]]
  
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
