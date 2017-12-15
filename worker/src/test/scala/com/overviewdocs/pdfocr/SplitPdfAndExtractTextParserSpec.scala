package com.overviewdocs.pdfocr

import com.google.common.io.ByteStreams
import java.io.{ByteArrayInputStream,InputStream}
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

import com.overviewdocs.util.AwaitMethod

class SplitPdfAndExtractTextParserSpec extends Specification {
  import scala.concurrent.ExecutionContext.Implicits.global
  import SplitPdfAndExtractTextParser.Token._

  trait BaseScope extends Scope with AwaitMethod {
    def size(n: Int): Array[Byte] = Array(n >> 24, n >> 16, n >> 8, n).map(_ & 0xff).map(_.toByte)
    def header(n: Int): Array[Byte] = Array(0x01.toByte) ++ size(n)
    val empty: Array[Byte] = Array.empty[Byte]
    def page(isOcr: Boolean, thumb: Array[Byte], pdf: Array[Byte], text: String): Array[Byte] = {
      val utf8 = text.getBytes("utf-8")
      (
        Array(0x02, if (isOcr) 1 else 0).map(_.toByte)
        ++ size(thumb.size) ++ thumb
        ++ size(pdf.size) ++ pdf
        ++ size(utf8.size) ++ utf8
      )
    }
    def footer(message: String): Array[Byte] = {
      val utf8 = message.getBytes("utf-8")
      Array(0x03.toByte) ++ size(utf8.size) ++ utf8
    }
    def inputBytes: Array[Byte] = empty
    def input: InputStream = new ByteArrayInputStream(inputBytes)
    lazy val parser = new SplitPdfAndExtractTextParser(input)
  }

  "SplitPdfAndExtractTextParser" should {
    "produce an nPages" in new BaseScope {
      override def inputBytes = header(1)
      await(parser.next) must beEqualTo(Header(1))
    }

    "produce a Page" in new BaseScope {
      override def inputBytes = header(1) ++ page(false, empty, empty, "text")
      await(parser.next)
      await(parser.next) must beEqualTo(Page(false, "text"))
    }

    "produce a Page with isOcr=true" in new BaseScope {
      override def inputBytes = header(1) ++ page(true, empty, empty, "text")
      await(parser.next)
      await(parser.next) must beEqualTo(Page(true, "text"))
    }

    "produce a PageThumbnail blob" in new BaseScope {
      override def inputBytes = header(1) ++ page(false, Array(5.toByte), empty, "text")
      await(parser.next)
      await(parser.next) match {
        case PageThumbnail(nBytes, inputStream) => {
          nBytes must beEqualTo(1)
          ByteStreams.toByteArray(inputStream) must beEqualTo(Array(5.toByte))
        }
        case _ => ???
      }
    }

    "produce a Page after a PageThumbnail if we read it" in new BaseScope {
      override def inputBytes = header(1) ++ page(false, Array(5.toByte), empty, "text")
      await(parser.next)
      await(parser.next) match {
        case PageThumbnail(_, inputStream) => ByteStreams.exhaust(inputStream)
        case _ => ???
      }
      await(parser.next) must beEqualTo(Page(false, "text"))
    }

    "produce a Page after a PageThumbnail if we don't read it" in new BaseScope {
      override def inputBytes = header(1) ++ page(false, Array(5.toByte), empty, "text")
      await(parser.next)
      await(parser.next)
      await(parser.next) must beEqualTo(Page(false, "text"))
    }

    "produce a PagePdf blob" in new BaseScope {
      override def inputBytes = header(1) ++ page(false, empty, Array(5.toByte), "text")
      await(parser.next)
      await(parser.next) match {
        case PagePdf(nBytes, inputStream) => {
          nBytes must beEqualTo(1)
          ByteStreams.toByteArray(inputStream) must beEqualTo(Array(5.toByte))
        }
        case _ => ???
      }
    }

    "produce a Page after a PagePdf if we read it" in new BaseScope {
      override def inputBytes = header(1) ++ page(false, empty, Array(5.toByte), "text")
      await(parser.next)
      await(parser.next) match {
        case PagePdf(nBytes, inputStream) => ByteStreams.exhaust(inputStream)
        case _ => ???
      }
      await(parser.next) must beEqualTo(Page(false, "text"))
    }

    "produce a Page after a PagePdf if we don't read it" in new BaseScope {
      override def inputBytes = header(1) ++ page(false, empty, Array(5.toByte), "text")
      await(parser.next)
      await(parser.next)
      await(parser.next) must beEqualTo(Page(false, "text"))
    }

    "produce a Page after a PageThumbnail and PagePdf" in new BaseScope {
      override def inputBytes = header(1) ++ page(false, Array(4.toByte), Array(5.toByte), "text")
      await(parser.next)
      await(parser.next)
      await(parser.next)
      await(parser.next) must beEqualTo(Page(false, "text"))
    }

    "produce a PdfError when input indicates an error in a PDF" in new BaseScope {
      override def inputBytes = footer("PDF error foo")
      await(parser.next) must beEqualTo(PdfError("PDF error foo"))
    }

    "produce a PdfError when input indicates an error in a PDF page" in new BaseScope {
      override def inputBytes = header(1) ++ footer("PDF error foo")
      await(parser.next)
      await(parser.next) must beEqualTo(PdfError("PDF error foo"))
    }

    "produce an InputError when header is malformed" in new BaseScope {
      override def inputBytes = Array(0x00.toByte)
      await(parser.next) must beEqualTo(InvalidInput("Error parsing HEADER"))
    }

    "produce an InputError when page is malformed" in new BaseScope {
      override def inputBytes = header(1) ++ Array(0x01.toByte)
      await(parser.next)
      await(parser.next) must beEqualTo(InvalidInput("Error parsing PAGE or FOOTER"))
    }

    "produce Success when done" in new BaseScope {
      override def inputBytes = header(1) ++ page(false, empty, empty, "text") ++ footer("")
      await(parser.next)
      await(parser.next)
      await(parser.next) must beEqualTo(Success)
    }

    "produce an InputError on unexpected EOF" in new BaseScope {
      override def inputBytes = header(2) ++ page(false, empty, empty, "text") ++ Array(0x02.toByte)
      await(parser.next)
      await(parser.next)
      await(parser.next) must beEqualTo(InvalidInput("Unexpected end of input"))
    }

    "produce an InputError on Java I/O error" in new BaseScope {
      override def input = new InputStream {
        override def read: Int = {
          throw new java.io.IOException("Error Foo")
        }
      }
      await(parser.next) must beEqualTo(InvalidInput("Exception: Error Foo"))
    }
  }
}
