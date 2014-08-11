package org.overviewproject.jobhandler.filegroup.task

import java.io.{BufferedInputStream,InputStream}
import java.util.UUID
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

import org.overviewproject.jobhandler.filegroup.task.MimeTypeDetectingDocumentConverter._ // exceptions

class MimeTypeDetectingDocumentConverterSpec extends Specification with Mockito {
  trait BaseScope extends Scope {
    val mockMimeTypeDetector = mock[MimeTypeDetectingDocumentConverter.MimeTypeDetector]
    val mockMimeTypeToConverter = mock[Map[String,DocumentConverter]]

    val converter = new MimeTypeDetectingDocumentConverter {
      override val mimeTypeDetector = mockMimeTypeDetector
      override val mimeTypeToConverter = mockMimeTypeToConverter
    }

    val inputStream = mock[InputStream]
    val guid = UUID.fromString("05ec18c5-bc34-4385-9fd0-f54519b9be97")
    val filename = "foo.txt"
    val mimeType = "text/plain"
    val parentMimeType = "text/*"

    def goodConverter(expectedValue: InputStream) = new DocumentConverter {
      override def withStreamAsPdf[T](guid: UUID, filename: String, inputStream: InputStream)(f: InputStream => T) = {
        f(expectedValue)
      }
    }

    mockMimeTypeDetector.nBytesNeededToDetectMimeType returns 1024
  }

  "MimeTypeDetectingDocumentConverter" should {
    "throw an exception on unhandled MIME type" in new BaseScope {
      mockMimeTypeDetector.detectMimeType(org.mockito.Matchers.eq(filename), any[BufferedInputStream]) returns mimeType
      mockMimeTypeToConverter.get(mimeType) returns None
      mockMimeTypeToConverter.get(parentMimeType) returns None

      converter.withStreamAsPdf(guid, filename, inputStream)(identity) must throwA[DocumentConverterDoesNotExistException]
    }

    "throw an exception when delegated DocumentConverter throws an exception" in new BaseScope {
      class FunkyException extends Exception

      mockMimeTypeDetector.detectMimeType(org.mockito.Matchers.eq(filename), any[BufferedInputStream]) returns mimeType
      mockMimeTypeToConverter.get(mimeType) returns Some(new DocumentConverter {
        override def withStreamAsPdf[T](guid: UUID, filename: String, inputStream: InputStream)(f: InputStream => T) = {
          throw new FunkyException
        }
      })

      converter.withStreamAsPdf(guid, filename, inputStream)(identity) must throwA[FunkyException]
    }

    "throw a CouldNotDetectMimeTypeException when MIME type detection fails" in new BaseScope {
      class FunkyException extends RuntimeException

      mockMimeTypeDetector.detectMimeType(org.mockito.Matchers.eq(filename), any[BufferedInputStream]) throws new FunkyException
      converter.withStreamAsPdf(guid, filename, inputStream)(identity) must throwA[CouldNotDetectMimeTypeException]
    }

    "convert a stream to PDF" in new BaseScope {
      val expectedValue = mock[InputStream]
      mockMimeTypeDetector.detectMimeType(org.mockito.Matchers.eq(filename), any[BufferedInputStream]) returns mimeType
      mockMimeTypeToConverter.get(mimeType) returns Some(goodConverter(expectedValue))

      val actualValue = converter.withStreamAsPdf(guid, filename, inputStream)(identity)
      actualValue must beEqualTo(expectedValue)
    }

    "convert a stream to PDF using text/* as MIME type" in new BaseScope {
      val expectedValue = mock[InputStream]
      mockMimeTypeDetector.detectMimeType(org.mockito.Matchers.eq(filename), any[BufferedInputStream]) returns "text/something-funny"
      mockMimeTypeToConverter.get("text/something-funny") returns None
      mockMimeTypeToConverter.get("text/*") returns Some(goodConverter(expectedValue))

      val actualValue = converter.withStreamAsPdf(guid, filename, inputStream)(identity)
      actualValue must beEqualTo(expectedValue)
    }
  }
}
