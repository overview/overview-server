package org.overviewproject.jobhandler.filegroup

trait PdfDocument {
  def pages: Iterable[PdfPage]
  def close(): Unit
}

case class PdfPage(data: Array[Byte], text: String)
