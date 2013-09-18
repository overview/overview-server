package models.export
import java.io.{ ByteArrayOutputStream, StringReader }

import play.api.Play.{ start, stop }
import play.api.test.FakeApplication

import org.overviewproject.tree.orm.{Document, Tag}
import org.overviewproject.tree.orm.finders.FinderResult
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope



import au.com.bytecode.opencsv.CSVReader

class ExportDocumentsWithColumnTagsSpec extends Specification with Mockito {
  step(start(FakeApplication()))

  trait BaseScope extends Scope {
    val tagFinderResult = mock[FinderResult[Tag]]
    tagFinderResult.toIterable returns Seq(
      Tag(id=1L, documentSetId=0L, name="tag1", color="000000"),
      Tag(id=2L, documentSetId=0L, name="tag2", color="000000")
    )

    val finderResult = mock[FinderResult[(Document,Option[String])]]
    val export = new ExportDocumentsWithColumnTags(finderResult, tagFinderResult)

    lazy val result : Array[Byte] = {
      val stream = new ByteArrayOutputStream
      export.exportTo(stream)
      stream.toByteArray
    }

    lazy val resultRows : Seq[Array[String]] = {
      import scala.collection.JavaConverters.asScalaBufferConverter
      val csv = new CSVReader(new StringReader(new String(result)))
      val rowsList = csv.readAll
      asScalaBufferConverter(rowsList).asScala
    }
  }

  trait OneDocumentScope extends BaseScope {
    def document : Document = Document()
    def documentTagIds : Option[String] = None
    finderResult.toIterable returns Seq((document, documentTagIds))
  }

  "ExportDocumentsWithColumnTags" should {
    "export when there are no tags" in new OneDocumentScope {
      tagFinderResult.toIterable returns Seq()
      resultRows.size must beEqualTo(2)
      resultRows(0).size must beEqualTo(3)
      resultRows(1).size must beEqualTo(3)
    }

    "export the tag names" in new OneDocumentScope {
      override def documentTagIds = None
      resultRows.size must beEqualTo(2)
      resultRows(0).size must beEqualTo(5)
      resultRows(0)(3) must beEqualTo("tag1")
      resultRows(0)(4) must beEqualTo("tag2")
    }

    "export when the document has no tags (NULL)" in new OneDocumentScope {
      override def documentTagIds = None
      resultRows.size must beEqualTo(2)
      resultRows(1).size must beEqualTo(5)
      resultRows(1)(3) must beEqualTo("")
      resultRows(1)(4) must beEqualTo("")
    }

    "export when the document has no tags (empty string)" in new OneDocumentScope {
      override def documentTagIds = Some("")
      resultRows.size must beEqualTo(2)
      resultRows(0).size must beEqualTo(5)
      resultRows(1).size must beEqualTo(5)
      resultRows(1)(3) must beEqualTo("")
      resultRows(1)(4) must beEqualTo("")
    }

    "export a document with a tag" in new OneDocumentScope {
      override def documentTagIds = Some("1")
      resultRows(1)(3) must beEqualTo("1")
      resultRows(1)(4) must beEqualTo("")
    }

    "export a document with multiple tags" in new OneDocumentScope {
      override def documentTagIds = Some("1,2")
      resultRows(1)(3) must beEqualTo("1")
      resultRows(1)(4) must beEqualTo("1")
    }

    "export Some(String) documentcloudId as suppliedId" in new OneDocumentScope {
      override def document = super.document.copy(documentcloudId=Some("documentcloud-id"))
      resultRows(1)(0) must beEqualTo("documentcloud-id")
    }

    "export Some(String) suppliedId" in new OneDocumentScope {
      override def document = super.document.copy(suppliedId=Some("supplied-id"))
      resultRows(1)(0) must beEqualTo("supplied-id")
    }

    "export Some(String) text" in new OneDocumentScope {
      override def document = super.document.copy(text=Some("text"))
      resultRows(1)(1) must beEqualTo("text")
    }

    "export Some(String) url" in new OneDocumentScope {
      override def document = super.document.copy(url=Some("http://example.org"))
      resultRows(1)(2) must beEqualTo("http://example.org")
    }

    "export Some(String) url from a DocumentCloud ID" in new OneDocumentScope {
      override def document = super.document.copy(documentcloudId=Some("documentcloud-id"))
      resultRows(1)(2) must contain("documentcloud-id")
    }
  }

  step(stop)
}
