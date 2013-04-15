package models.export

import java.io.{ ByteArrayOutputStream }
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.Play.{ start, stop }
import play.api.test.FakeApplication

import org.overviewproject.util.TempFile
import org.overviewproject.tree.orm.{ Document, DocumentType }
import models.orm.finders.FinderResult

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
  }

  trait OneDocumentScope extends BaseScope {
    def document : Document = Document(documentType=DocumentType.DocumentCloudDocument)
    def tags : Option[String] = None
    finderResult.toIterable returns Seq((document, tags))
  }

  "ExportDocumentsWithStringTags" should {
    "export None everything" in new OneDocumentScope {
      new String(result) must contain(",")
    }

    "export Some(String) tags" in new OneDocumentScope {
      override def tags = Some("tag1,tag2")
      new String(result) must contain("\"tag1,tag2\"")
    }

    "export Some(String) suppliedId" in new OneDocumentScope {
      override def document = super.document.copy(suppliedId=Some("supplied-id"))
      new String(result) must contain("supplied-id")
    }

    "export Some(String) url" in new OneDocumentScope {
      override def document = super.document.copy(url=Some("http://example.org"))
      new String(result) must contain("http://example.org")
    }

    "export Some(String) text" in new OneDocumentScope {
      override def document = super.document.copy(text=Some("text"))
      new String(result) must contain("text")
    }
  }

  step(stop)
}
