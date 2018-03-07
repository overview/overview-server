package com.overviewdocs.models

import org.specs2.mutable.Specification

class DocumentInfoSpec extends Specification {
  def buildDocumentInfo(id: Long, url: Option[String], hasFileView: Boolean, file2Id: Option[Long]): DocumentInfo = DocumentInfo(
    id,
    1234L,
    url,
    "",
    "",
    None,
    new java.util.Date(0L),
    DocumentDisplayMethod.auto,
    file2Id,
    false,
    hasFileView,
    None
  )

  "#viewUrl" should {
    "return the url if there is one" in {
      val documentInfo = buildDocumentInfo(id=1L, url=Some("http://foo.com"), hasFileView=false, file2Id=None)
      documentInfo.viewUrl must beSome("http://foo.com")
    }

    "return the url even if there is a file" in {
      val documentInfo = buildDocumentInfo(id=1L, url=Some("http://foo.com"), hasFileView=true, file2Id=None)
      documentInfo.viewUrl must beSome("http://foo.com")
    }

    "return an Overview PDF URL if there is no URL but a file was uploaded" in {
      val documentInfo = buildDocumentInfo(id=1L, url=None, hasFileView=true, file2Id=None)
      documentInfo.viewUrl must beSome("/documents/1.pdf")
    }

    "return an Overview PDF URL if there is no URL but a file2 was uploaded" in {
      val documentInfo = buildDocumentInfo(id=1L, url=None, hasFileView=false, file2Id=Some(123L))
      documentInfo.viewUrl must beSome("/documents/1.pdf")
    }

    "return None if there is no way to generate a URL" in {
      val documentInfo = buildDocumentInfo(id=1L, url=None, hasFileView=false, file2Id=None)
      documentInfo.viewUrl must beNone
    }
  }
}
