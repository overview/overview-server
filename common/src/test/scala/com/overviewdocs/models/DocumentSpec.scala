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
      new java.util.Date(0L),
      fileId,
      None,
      DocumentDisplayMethod.auto,
      false,
      JsObject(Seq()),
      None,
      PdfNoteCollection(Array()),
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
      new java.util.Date(0L),
      None,
      None,
      DocumentDisplayMethod.auto,
      false,
      JsObject(Seq()),
      None,
      PdfNoteCollection(Array()),
      text
    )

    "tokenize the text" in {
      val document = documentWithText("foo bar baz")
      document.tokens must beEqualTo(Seq("foo", "bar", "baz"))
    }

    "tokenize an empty string" in {
      documentWithText("").tokens must beEqualTo(Seq())
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

    "handle multiple scripts in one document" in {
      val document = documentWithText("bar marバーマルบาร์ มี.ค.바 월moo")
      document.tokens must beEqualTo(Seq("bar", "mar", "バー", "マル", "บาร์", "มี.ค", "바", "월", "moo"))
    }

    "handle codepoint position, not character position, when switching between CJK and Latin" in {
      val document = documentWithText("时间: 10\n年")
      val tokens = document.tokens
      document.tokens must beEqualTo(Seq("时间", "10", "年"))
    }
  }
}
