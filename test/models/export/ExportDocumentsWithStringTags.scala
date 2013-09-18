package models.export

import java.io.{ ByteArrayOutputStream, StringReader }

import play.api.Play.{ start, stop }
import play.api.test.FakeApplication

import org.overviewproject.tree.orm.Document
import org.overviewproject.tree.orm.finders.FinderResult
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope



import au.com.bytecode.opencsv.CSVReader

class ExportDocumentsWithStringTagsSpec extends Specification with Mockito {
  step(start(FakeApplication()))

  trait BaseScope extends Scope {
    val finderResult = mock[FinderResult[(Document,Option[String])]]
    val contents : Array[Byte] = "foobar".getBytes

    val export = new ExportDocumentsWithStringTags(finderResult)

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
    def tags : Option[String] = None
    finderResult.toIterable returns Seq((document, tags))
    lazy val resultRow = resultRows(1)
  }

  "ExportDocumentsWithStringTags" should {
    "export None everything" in new OneDocumentScope {
      resultRows.size must beEqualTo(2)
      resultRow.size must beEqualTo(4)
    }

    "export Some(String) tags" in new OneDocumentScope {
      override def tags = Some("tag1,tag2")
      resultRow(3) must beEqualTo("tag1,tag2")
    }

    "export Some(String) suppliedId" in new OneDocumentScope {
      override def document = super.document.copy(suppliedId=Some("supplied-id"))
      resultRow(0) must beEqualTo("supplied-id")
    }

    "export Some(String) suppliedId when it is a DocumentCloud ID" in new OneDocumentScope {
      override def document = super.document.copy(documentcloudId=Some("documentcloud-id"))
      resultRow(0) must beEqualTo("documentcloud-id")
    }

    "export Some(String) url" in new OneDocumentScope {
      override def document = super.document.copy(url=Some("http://example.org"))
      resultRow(2) must beEqualTo("http://example.org")
    }

    "export Some(String) URL when there is only a DocumentCloud ID" in new OneDocumentScope {
      override def document = super.document.copy(documentcloudId=Some("documentcloud-id"))
      resultRow(2) must contain("documentcloud-id")
    }

    "export Some(String) text" in new OneDocumentScope {
      override def document = super.document.copy(text=Some("text"))
      resultRow(1) must beEqualTo("text")
    }
  }

  step(stop)
}
