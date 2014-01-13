package models.export.rows

import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

import org.overviewproject.tree.orm.Document
import org.overviewproject.tree.orm.finders.FinderResult

class DocumentsWithStringTagsSpec extends Specification with Mockito {
  trait BaseScope extends Scope {
    val finderResult = mock[FinderResult[(Document,Option[String])]]
    val rows = new DocumentsWithStringTags(finderResult)

    def outHeaders = rows.headers
    def outRows = rows.rows
  }

  trait OneDocumentScope extends BaseScope {
    def document : Document = Document()
    def tags : Option[String] = None
    finderResult.toIterable returns Seq((document, tags))

    def outRow = outRows.head.map(_.toString).toSeq
  }

  "ExportDocumentsWithStringTags" should {
    "export Some(String) tags" in new OneDocumentScope {
      override def tags = Some("tag1,tag2")
      outRow(4) must beEqualTo("tag1,tag2")
    }

    "export Some(String) suppliedId" in new OneDocumentScope {
      override def document = super.document.copy(suppliedId=Some("supplied-id"))
      outRow(0) must beEqualTo("supplied-id")
    }

    "export Some(String) suppliedId when it is a DocumentCloud ID" in new OneDocumentScope {
      override def document = super.document.copy(documentcloudId=Some("documentcloud-id"))
      outRow(0) must beEqualTo("documentcloud-id")
    }

    "export Some(String) title" in new OneDocumentScope {
      override def document = super.document.copy(title=Some("title"))
      outRow(1).toString must beEqualTo("title")
    }

    "export None title" in new OneDocumentScope {
      override def document = super.document.copy(title=None)
      outRow(1).toString must beEqualTo("")
    }

    "export Some(String) url" in new OneDocumentScope {
      override def document = super.document.copy(url=Some("http://example.org"))
      outRow(3) must beEqualTo("http://example.org")
    }

    "export Some(String) URL when there is only a DocumentCloud ID" in new OneDocumentScope {
      override def document = super.document.copy(documentcloudId=Some("documentcloud-id"))
      outRow(3) must contain("documentcloud-id")
    }

    "export Some(String) text" in new OneDocumentScope {
      override def document = super.document.copy(text=Some("text"))
      outRow(2) must beEqualTo("text")
    }
  }
}
