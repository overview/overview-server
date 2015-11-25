package com.overviewdocs.csv

import java.io.StringReader
import org.specs2.specification.Scope
import com.overviewdocs.test.Specification

class CsvDocumentProducerSpec extends Specification {
  "CsvDocumentProducer" should {
    trait BaseScope extends Scope {
      val input: String = ""
      val producer = new CsvDocumentProducer
      lazy val documents = {
        val arrays = input.split("\n").map(_.split(","))
        arrays.foreach(producer.addCsvRow)
        producer.getProducedDocuments
      }
      def metadataColumnNames = {
        documents // parse input
        producer.metadataColumnNames
      }
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
      documents.length must beEqualTo(0)
    }

    "handle a just-newlines CSV file" in new BaseScope {
      override val input="\n\n"
      documents.length must beEqualTo(0)
    }

    "find a suppliedId column named `id`" in new BaseScope {
      override val input = "text,id\nline0,id0\nline1,id1"
      documents.map(_.suppliedId) must beEqualTo(Seq("id0", "id1"))
      documents.map(_.metadata) must beEqualTo(Seq(Seq(), Seq()))
    }

    "find a url column named `url`" in new BaseScope {
      override val input = "text,url\nline0,url0\nline1,url1"
      documents.map(_.url) must beEqualTo(Seq("url0", "url1"))
      documents.map(_.metadata) must beEqualTo(Seq(Seq(), Seq()))
    }

    "find a title column named `title`" in new BaseScope {
      override val input = "text,title\nline0,title0\nline1,title1"
      documents.map(_.title) must beEqualTo(Seq("title0", "title1"))
      documents.map(_.metadata) must beEqualTo(Seq(Seq(), Seq()))
    }

    "find a column named `tags`" in new BaseScope {
      producer.addCsvRow(Array("text", "tags"))
      producer.addCsvRow(Array("line0", "foo,bar"))
      producer.addCsvRow(Array("line1", "bar,baz"))
      producer.getProducedDocuments.map(_.tags) must beEqualTo(Seq(Set("foo", "bar"), Set("bar", "baz")))
      producer.getProducedDocuments.map(_.metadata) must beEqualTo(Seq(Seq(), Seq()))
    }

    "handle uppercase suppliedId, text, tags and url headers" in new BaseScope {
      override val input = "TEXT,ID,URL,TITLE,TAGS\ntext0,id0,url0,title0,tag0"
      documents.head must beEqualTo(CsvDocument("id0", "url0", "title0", Set("tag0"), "text0", Seq()))
    }

    "textify all fields" in new BaseScope {
      // As a side-effect, this tests that the byte-order marker is stripped
      override val input = """|\ufffetext,title
                              |foo\u0000bar,bar\ufffebaz""".stripMargin
      val doc = documents.head
      doc.text must beEqualTo("foo bar")
      doc.title must beEqualTo("barbaz")
    }

    "fail if there is no `text` on the first line" in new BaseScope {
      override val input = "foo\nbar"
      documents.head.text must beEqualTo("")
    }

    "leave suppliedId empty when header has no `id`" in new BaseScope {
      override val input = "text\nfoo"
      documents.head.suppliedId must beEqualTo("")
    }

    "leave url empty when header has no `url`" in new BaseScope {
      override val input = "text\nfoo"
      documents.head.url must beEqualTo("")
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
      producer.addCsvRow(Array("text", "tags"))
      producer.addCsvRow(Array("text0", "foo,bar,foo"))
      producer.getProducedDocuments.head.tags must beEqualTo(Set("foo", "bar"))
    }

    "ignore empty tags" in new BaseScope {
      producer.addCsvRow(Array("text", "tags"))
      producer.addCsvRow(Array("text0", "foo,,bar"))
      producer.addCsvRow(Array("text1", ","))
      producer.addCsvRow(Array("text2", ""))
      override val input = """|text,tags
                              |text0,"foo,,bar"
                              |text1,","""".stripMargin
      producer.getProducedDocuments.map(_.tags) must beEqualTo(Seq(Set("foo", "bar"), Set.empty, Set.empty))
    }

    "treat non-id/text/url/title/tags as metadata" in new BaseScope {
      override val input = """|foo,bar,id,text,url,title,tags
                              |foo0,bar0
                              |foo1,bar1""".stripMargin
      metadataColumnNames must beEqualTo(Seq("foo", "bar"))
      documents.map(_.metadata) must beEqualTo(Seq(
        Seq("foo" -> "foo0", "bar" -> "bar0"),
        Seq("foo" -> "foo1", "bar" -> "bar1")
      ))
    }

    "ignore a second metadata field of the same name" in new BaseScope {
      override val input = """|text,f,f
                              |text0,foo,bar
                              |text1,moo,mar""".stripMargin
      metadataColumnNames must beEqualTo(Seq("f"))
      documents.map(_.metadata) must beEqualTo(Seq(Seq("f" -> "foo"), Seq("f" -> "moo")))
    }

    "ignore an empty-name metadata field" in new BaseScope {
      override val input = "text,f,\ntext,foo"
      metadataColumnNames must beEqualTo(Seq("f"))
      documents.map(_.metadata) must beEqualTo(Seq(Seq("f" -> "foo")))
    }

    "treat contents/snippet as metadata when there is `text`" in new BaseScope {
      override val input = "contents,snippet,text\nfoo,bar"
      metadataColumnNames must beEqualTo(Seq("contents", "snippet"))
      documents.head.metadata must beEqualTo(Seq("contents" -> "foo", "snippet" -> "bar"))
    }

    "treat empty metadata as empty string" in new BaseScope {
      override val input = "text,foo,bar\ntext0,"
      metadataColumnNames must beEqualTo(Seq("foo", "bar"))
      documents.head.metadata must beEqualTo(Seq("foo" -> "", "bar" -> ""))
    }
  }
}
