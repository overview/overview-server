package com.overviewdocs.models

import org.specs2.mutable.Specification
import play.api.libs.json.JsObject

class DocumentSpec extends Specification {
  "#viewUrl" should {
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
      DocumentDisplayMethod.auto,
      JsObject(Seq()),
      ""
    )

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

  "#tokens" should {
    def documentWithText(text: String): Document = Document(
      1L,
      1234L,
      None,
      "",
      "",
      None,
      Seq(),
      new java.util.Date(0L),
      None,
      None,
      DocumentDisplayMethod.auto,
      JsObject(Seq()),
      text
    )

    "tokenize the text" in {
      val document = documentWithText("foo bar baz")
      document.tokens must beEqualTo(Seq("foo", "bar", "baz"))
    }

    "handle iffy tokens" in {
      // We don't test *everything* here; we just prove our tokenizer isn't simple
      val document = documentWithText("Mr. Foo's 'bar' ... 1.5")
      document.tokens must beEqualTo(Seq("Mr", "Foo's", "bar", "1.5"))
    }

    "normalize to NFKC" in {
      val document = documentWithText("caf\u0065\u0301⁵")
      document.tokens must beEqualTo(Seq("caf\u00e95"))
    }
  }
}
