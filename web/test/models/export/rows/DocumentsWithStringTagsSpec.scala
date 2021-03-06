package models.export.rows

import akka.stream.scaladsl.{Sink,Source}
import org.specs2.specification.Scope
import play.api.libs.json.Json
import play.api.test.{DefaultAwaitTimeout,FutureAwaits}
import scala.collection.immutable

import com.overviewdocs.metadata.{Metadata,MetadataField,MetadataFieldType,MetadataSchema}
import com.overviewdocs.models.{Document,Tag}
import test.helpers.InAppSpecification // for materializer

class DocumentsWithStringTagsSpec extends InAppSpecification with FutureAwaits with DefaultAwaitTimeout {
  trait BaseScope extends Scope {
    val factory = com.overviewdocs.test.factories.PodoFactory
    def documents: Source[(Document, Vector[Long]), akka.NotUsed]
    val metadataSchema: MetadataSchema = MetadataSchema.empty
    val tags: Vector[Tag] = Vector()
    lazy val rows: Rows = DocumentsWithStringTags(metadataSchema, documents, tags)
    lazy val rowList: immutable.Seq[Array[String]] = await(rows.rows.runWith(Sink.seq))
    def headRow = rowList.head
  }

  trait OneDocumentScope extends BaseScope {
    val sampleDocument: Document = factory.document(
      suppliedId="suppliedId",
      title="title",
      text="text",
      url=Some("url"),
      metadataJson=Json.obj()
    )
    val document: (Document,Vector[Long]) = (sampleDocument, Vector())
    override def documents = Source.single(document)
  }

  "ExportDocumentsWithStringTags" should {
    "export tags" in new OneDocumentScope {
      override val tags = Vector(
        factory.tag(id=2L, name="aaa"), // IDs out of order so we test ordering
        factory.tag(id=3L, name="bbb"),
        factory.tag(id=1L, name="ccc")
      )
      override val document = (sampleDocument, Vector(1L, 3L))
      headRow(4) must beEqualTo("bbb,ccc")
    }

    "export suppliedId" in new OneDocumentScope {
      override val document = (sampleDocument.copy(suppliedId="foobar"), Vector())
      headRow(0) must beEqualTo("foobar")
    }

    "export title" in new OneDocumentScope {
      override val document = (sampleDocument.copy(title="foobar"), Vector())
      headRow(1).toString must beEqualTo("foobar")
    }

    "export url" in new OneDocumentScope {
      override val document = (sampleDocument.copy(url=Some("foobar")), Vector())
      headRow(3) must beEqualTo("foobar")
    }

    "export None url" in new OneDocumentScope {
      override val document = (sampleDocument.copy(url=None), Vector())
      headRow(3) must beEqualTo("")
    }

    "export text" in new OneDocumentScope {
      override val document = (sampleDocument.copy(text="foobar"), Vector())
      headRow(2) must beEqualTo("foobar")
    }

    "export metadata" in new OneDocumentScope {
      override val metadataSchema = MetadataSchema(1, Vector(
        MetadataField("fooField", MetadataFieldType.String),
        MetadataField("barField", MetadataFieldType.String)
      ))
      override val document = (sampleDocument.copy(metadataJson=Json.obj("fooField" -> "foo1", "barField" -> "bar1")), Vector())
      rows.headers.drop(4).take(2) must beEqualTo(Array("fooField", "barField")) // metadata before tags
      headRow.drop(4).take(2) must beEqualTo(Array("foo1", "bar1"))
    }
  }
}
