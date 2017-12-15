package com.overviewdocs.pdfocr

import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class SplitPdfAndExtractTextParserSpec extends Specification {
  sequential

  trait BaseScope extends Scope {
    def size(n: Int): Array[Byte] = Array(n >> 24, n >> 16, n >> 8, n).map(_ & 0xff).map(_.toByte)
    def header(n: Int): Array[Byte] = Array(0x01.toByte) ++ size
    def page(isOcr: Boolean, thumb: Array[Byte], pdf: Array[Byte], text: String): Array[Byte] = {
      val utf8 = text.toBytes("utf-8")
      Array(0x02.toByte, isOcr ? 1.toByte : 0.toByte)
        ++ size(thumb.size) ++ thumb
        ++ size(pdf.size) ++ pdf
        ++ size(utf8.size) ++ utf8
    }
    def footer(message: String): Array[Byte] = {
      val utf8 = message.toBytes("utf-8")
      Array(0x03.toByte) ++ size(utf8.size) ++ utf8
    }
    def bytes: Array[Byte]
  }

  "SplitPdfAndExtractTextParser" should {


    "produce a Page" in pending
    "produce the right Page numbers" in pending
    "produce a PageThumbnail blob" in pending
    "produce a Page after a PageThumbnail" in pending
    "produce a PagePdf blob" in pending
    "produce a Page after a PageThumbnail and PagePdf" in pending
    "produce a PdfError when input indicates an error in a PDF" in pending
    "produce a InputError when input is malformed" in pending
    "prooduce Done when done" in pending
  }
}
