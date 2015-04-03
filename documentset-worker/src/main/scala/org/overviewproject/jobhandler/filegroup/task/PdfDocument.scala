package org.overviewproject.jobhandler.filegroup.task

import scala.collection.SeqView

trait PdfDocument {
  def pages: SeqView[PdfPage, Seq[_]]
  def text: String
  def close(): Unit
}

case class PdfPage(data: Array[Byte], text: String)
