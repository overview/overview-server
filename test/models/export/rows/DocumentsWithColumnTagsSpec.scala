package models.export.rows

import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

import org.overviewproject.tree.orm.{Document,Tag}
import org.overviewproject.tree.orm.finders.FinderResult

class DocumentsWithColumnTagsSpec extends Specification with Mockito {
  trait BaseScope extends Scope {
    val tagFinderResult = mock[FinderResult[Tag]]
    val finderResult = mock[FinderResult[(Document,Option[String])]]
    val rows = new DocumentsWithColumnTags(finderResult, tagFinderResult)

    def outHeaders = rows.headers.toIndexedSeq
    def outRows = rows.rows
    def outRow1 = rows.rows.head.toSeq
  }

  trait OneDocumentScope extends BaseScope {
    def document : Document = Document()
    def documentTagIds : Option[String] = None
    finderResult.toIterable returns Seq((document, documentTagIds))

    tagFinderResult.toIterable returns Seq(
      Tag(id=1L, documentSetId=0L, name="tag1", color="000000"),
      Tag(id=2L, documentSetId=0L, name="tag2", color="000000")
    )
  }

  "DocumentsWithColumnTags" should {
    "export when there are no tags" in new OneDocumentScope {
      tagFinderResult.toIterable returns Seq()
      outRows.toSeq.length must beEqualTo(1)
    }

    "export the tag names" in new OneDocumentScope {
      override def documentTagIds = None
      outHeaders.length must beEqualTo(6)
      outHeaders(4).toString must beEqualTo("tag1")
      outHeaders(5).toString must beEqualTo("tag2")
    }

    "export when the document has no tags (NULL)" in new OneDocumentScope {
      override def documentTagIds = None
      outRow1.length must beEqualTo(6)
      outRow1(4).toString must beEqualTo("")
      outRow1(5).toString must beEqualTo("")
    }

    "export when the document has no tags (empty string)" in new OneDocumentScope {
      override def documentTagIds = Some("")
      outRow1.length must beEqualTo(6)
      outRow1(4).toString must beEqualTo("")
      outRow1(5).toString must beEqualTo("")
    }

    "export a document with a tag" in new OneDocumentScope {
      override def documentTagIds = Some("1")
      outRow1(4).toString must beEqualTo("1")
      outRow1(5).toString must beEqualTo("")
    }

    "export a document with multiple tags" in new OneDocumentScope {
      override def documentTagIds = Some("1,2")
      outRow1(4).toString must beEqualTo("1")
      outRow1(5).toString must beEqualTo("1")
    }

    "export Some(String) documentcloudId as suppliedId" in new OneDocumentScope {
      override def document = super.document.copy(documentcloudId=Some("documentcloud-id"))
      outRow1(0).toString must beEqualTo("documentcloud-id")
    }

    "export Some(String) suppliedId" in new OneDocumentScope {
      override def document = super.document.copy(suppliedId=Some("supplied-id"))
      outRow1(0).toString must beEqualTo("supplied-id")
    }

    "export Some(String) title" in new OneDocumentScope {
      override def document = super.document.copy(title=Some("title"))
      outRow1(1).toString must beEqualTo("title")
    }

    "export None title" in new OneDocumentScope {
      override def document = super.document.copy(title=None)
      outRow1(1).toString must beEqualTo("")
    }

    "export Some(String) text" in new OneDocumentScope {
      override def document = super.document.copy(text=Some("text"))
      outRow1(2).toString must beEqualTo("text")
    }

    "export Some(String) url" in new OneDocumentScope {
      override def document = super.document.copy(url=Some("http://example.org"))
      outRow1(3).toString must beEqualTo("http://example.org")
    }

    "export Some(String) url from a DocumentCloud ID" in new OneDocumentScope {
      override def document = super.document.copy(documentcloudId=Some("documentcloud-id"))
      outRow1(3).toString must contain("documentcloud-id")
    }
  }
}
