package com.overviewdocs.csv

import java.io.StringReader
import org.specs2.specification.Scope
import com.overviewdocs.test.Specification

class CsvImportSourceSpec extends Specification {
  "CsvImportSource" should {
    trait BaseScope extends Scope {
      val input: String
      def textify(s: String) = """[\u0000\ufffe]""".r.replaceAllIn(s, "")
      lazy val reader = new StringReader(input)
      lazy val csvImportSource = new CsvImportSource(textify, reader)
      lazy val documents = csvImportSource.toSeq
    }

    "find a text column named `text`" in new BaseScope {
      override val input = "text\nline0\nline1\nline2"
      documents.map(_.text) must beEqualTo(Seq("line0", "line1", "line2"))
    }

    "handle a zero-doc CSV file" in new BaseScope {
      override val input="text"
      documents.length must beEqualTo(0)
    }

    "fail with an empty CSV file" in new BaseScope {
      override val input=""
      documents.length must throwA[RuntimeException]
    }

    "handle a just-newlines CSV file" in new BaseScope {
      override val input="\n\n"
      documents.length must throwA[RuntimeException]
    }

    "find a suppliedId column named `id`" in new BaseScope {
      override val input = "text,id\nline0,id0\nline1,id1"
      documents.map(_.suppliedId) must beEqualTo(Seq("id0", "id1"))
      documents.map(_.metadata) must beEqualTo(Seq(Map(), Map()))
    }

    "find a url column named `url`" in new BaseScope {
      override val input = "text,url\nline0,url0\nline1,url1"
      documents.map(_.url) must beEqualTo(Seq(Some("url0"), Some("url1")))
      documents.map(_.metadata) must beEqualTo(Seq(Map(), Map()))
    }

    "find a title column named `title`" in new BaseScope {
      override val input = "text,title\nline0,title0\nline1,title1"
      documents.map(_.title) must beEqualTo(Seq("title0", "title1"))
      documents.map(_.metadata) must beEqualTo(Seq(Map(), Map()))
    }

    "find a column named `tags`" in new BaseScope {
      override val input = """|text,tags
                            |line0,"foo,bar"
                            |line1,"bar,baz"""".stripMargin
      documents.map(_.tags) must beEqualTo(Seq(Set("foo", "bar"), Set("bar", "baz")))
      documents.map(_.metadata) must beEqualTo(Seq(Map(), Map()))
    }

    "handle uppercase suppliedId, text, tags and url headers" in new BaseScope {
      override val input = "TEXT,ID,URL,TITLE,TAGS\ntext0,id0,url0,title0,tag0"
      documents.head must beEqualTo(CsvImportDocument("text0", "id0", Some("url0"), "title0", Set("tag0"), Map.empty))
    }

    "textify all fields" in new BaseScope {
      // As a side-effect, this tests that the byte-order marker is stripped
      override val input = """|\ufffetext,title\u0000
                              |foo\u0000bar,bar\ufffebaz""".stripMargin
      val doc = documents.head
      doc.text must beEqualTo("foobar")
      doc.title must beEqualTo("barbaz")
    }

    "fail if there is no `text` on the first line" in new BaseScope {
      override val input = "foo\nbar"
      documents.head must throwA[Exception]
    }

    "leave suppliedId empty when header has no `id`" in new BaseScope {
      override val input = "text\nfoo"
      documents.head.suppliedId must beEqualTo("")
    }

    "leave url None when header has no `url`" in new BaseScope {
      override val input = "text\nfoo"
      documents.head.url must beNone
    }

    "leave title empty when header has no `title`" in new BaseScope {
      override val input = "text\nfoo"
      documents.head.title must beEqualTo("")
    }

    "don't interpret backslash as escape" in new BaseScope {
      override val input = """|text,id
                              |some text\"aa\"bb,34""".stripMargin
      val doc = documents.head
      doc.text must beEqualTo("""some text\"aa\"bb""")
      doc.suppliedId must beEqualTo("34")
    }

    "give documents empty text when text column does not appear on a row" in new BaseScope {
      override val input = "id,text\nfoo"
      documents.head.text must beEqualTo("")
    }

    "make `contents` an alias for `text`" in new BaseScope {
      override val input = "contents\nfoobar"
      documents.head.text must beEqualTo("foobar")
    }

    "make `snippet` an alias for `text`" in new BaseScope {
      override val input = "snippet\nfoobar"
      documents.head.text must beEqualTo("foobar")
    }

    "make `text` override `contents` and `snippet`" in new BaseScope {
      override val input = "snippet,contents,text\nsnippet0,contents0,text0"
      documents.head.text  must beEqualTo("text0")
    }

    "ignore repeated tags" in new BaseScope {
      override val input = """|text,tags
                              |text0,"foo,bar,foo"""".stripMargin
      documents.head.tags must beEqualTo(Set("foo", "bar"))
    }

    "ignore empty tags" in new BaseScope {
      override val input = """|text,tags
                              |text0,"foo,,bar"
                              |text1,","""".stripMargin
      documents.map(_.tags) must beEqualTo(Seq(Set("foo", "bar"), Set.empty))
    }

    "treat non-id/text/url/title/tags as metadata" in new BaseScope {
      override val input = """|foo,bar,id,text,url,title,tags
                              |foo0,bar0
                              |foo1,bar1""".stripMargin
      csvImportSource.metadataColumnNames must beEqualTo(Seq("foo", "bar"))
      documents.map(_.metadata) must beEqualTo(Seq(
        Map("foo" -> "foo0", "bar" -> "bar0"),
        Map("foo" -> "foo1", "bar" -> "bar1")
      ))
    }

    "ignore a second metadata field of the same name" in new BaseScope {
      override val input = """|text,f,f
                              |text0,foo,bar
                              |text1,moo,mar""".stripMargin
      csvImportSource.metadataColumnNames must beEqualTo(Seq("f"))
      documents.map(_.metadata) must beEqualTo(Seq(Map("f" -> "foo"), Map("f" -> "moo")))
    }

    "ignore an empty-name metadata field" in new BaseScope {
      override val input = "text,f,\ntext,foo"
      csvImportSource.metadataColumnNames must beEqualTo(Seq("f"))
      documents.map(_.metadata) must beEqualTo(Seq(Map("f" -> "foo")))
    }

    "treat contents/snippet as metadata when there is `text`" in new BaseScope {
      override val input = "contents,snippet,text\nfoo,bar"
      csvImportSource.metadataColumnNames must beEqualTo(Seq("contents", "snippet"))
      documents.head.metadata must beEqualTo(Map("contents" -> "foo", "snippet" -> "bar"))
    }

    "treat empty metadata as empty string" in new BaseScope {
      override val input = "text,foo,bar\ntext0,"
      csvImportSource.metadataColumnNames must beEqualTo(Seq("foo", "bar"))
      documents.head.metadata must beEqualTo(Map("foo" -> "", "bar" -> ""))
    }
  }
}
