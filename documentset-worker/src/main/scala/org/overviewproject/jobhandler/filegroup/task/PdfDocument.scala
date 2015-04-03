package org.overviewproject.jobhandler.filegroup.task

import scala.collection.SeqView

trait PdfDocument {
  /** @returns a View to prevent all page data to be loaded into memory at once */
  def pages: SeqView[PdfPage, Seq[_]]
  def text: String
  def close(): Unit
}

case class PdfPage(data: Array[Byte], text: String)
