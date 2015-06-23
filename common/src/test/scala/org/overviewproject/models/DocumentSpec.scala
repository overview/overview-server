package org.overviewproject.models

import org.specs2.mutable.Specification
import play.api.libs.json.JsObject

class DocumentSpec extends Specification {
  def buildDocument(id: Long, url: Option[String], fileId: Option[Long]): Document = Document(
    id,
    1234L,
    url,
    "",
    "",
    None,
    Seq(),
    new java.util.Date(0L),
    fileId,
    None,
    None,
    JsObject(Seq()),
    ""
  )

  "#viewUrl" should {
    "return the url if there is one" in {
      val document = buildDocument(id=1L, url=Some("http://foo.com"), fileId=None)
      document.viewUrl must beSome("http://foo.com")
    }

    "return the url even if there is a file" in {
      val document = buildDocument(id=1L, url=Some("http://foo.com"), fileId=Some(1L))
      document.viewUrl must beSome("http://foo.com")
    }

    "return an Overview PDF URL if there is no URL but a file was uploaded" in {
      val document = buildDocument(id=1L, url=None, fileId=Some(2L))
      document.viewUrl must beSome("/documents/1.pdf")
    }

    "return None if there is no way to generate a URL" in {
      val document = buildDocument(id=1L, url=None, fileId=None)
      document.viewUrl must beNone
    }
  }
}
