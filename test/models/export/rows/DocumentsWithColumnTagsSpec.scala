package models.export.rows

import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.libs.iteratee.{Enumerator,Iteratee}
import play.api.test.{DefaultAwaitTimeout,FutureAwaits}

import org.overviewproject.models.Tag

class DocumentsWithColumnTagsSpec extends Specification with FutureAwaits with DefaultAwaitTimeout {
  trait BaseScope extends Scope {
    val factory = org.overviewproject.test.factories.PodoFactory
    def documents: Enumerator[DocumentForCsvExport]
    val tags: Seq[Tag] = Seq()
    lazy val rows: Rows = DocumentsWithColumnTags(documents, tags)
    lazy val rowList: List[Array[String]] = await(rows.rows.run(Iteratee.getChunks))
    def outHeaders = rows.headers
    def outRow1 = rowList.head
  }

  trait OneDocumentScope extends BaseScope {
    val sampleDocument = DocumentForCsvExport("suppliedId", "title", "text", "url", Seq())
    val document = sampleDocument
    override def documents = Enumerator(document)
  }

  "DocumentsWithColumnTags" should {
    "export when there are no tags" in new OneDocumentScope {
      rowList.length must beEqualTo(1)
    }

    "export all tag names" in new OneDocumentScope {
      override val tags = Seq(
        factory.tag(id=2L, name="aaa"), // IDs out of order so we test ordering
        factory.tag(id=3L, name="bbb"),
        factory.tag(id=1L, name="ccc")
      )
      outHeaders.drop(4) must beEqualTo(Array("aaa", "bbb", "ccc"))
    }

    "export when the document has no tags" in new OneDocumentScope {
      override val tags = Seq(factory.tag(), factory.tag())
      outRow1.drop(4) must beEqualTo(Array("", ""))
    }

    "export document tags" in new OneDocumentScope {
      override val tags = Seq(
        factory.tag(id=2L, name="aaa"), // IDs out of order so we test ordering
        factory.tag(id=3L, name="bbb"),
        factory.tag(id=1L, name="ccc")
      )
      override val document = sampleDocument.copy(tagIds=Seq(1L, 3L))
      outRow1.drop(4) must beEqualTo(Array("", "1", "1"))
    }

    "export suppliedId" in new OneDocumentScope {
      override val document = sampleDocument.copy(suppliedId="supplied-id")
      outRow1(0).toString must beEqualTo("supplied-id")
    }

    "export title" in new OneDocumentScope {
      override val document = sampleDocument.copy(title="title")
      outRow1(1).toString must beEqualTo("title")
    }

    "export text" in new OneDocumentScope {
      override val document = sampleDocument.copy(text="text")
      outRow1(2).toString must beEqualTo("text")
    }

    "export url" in new OneDocumentScope {
      override val document = sampleDocument.copy(url="http://example.org")
      outRow1(3).toString must beEqualTo("http://example.org")
    }
  }
}
