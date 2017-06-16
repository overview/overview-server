package models.export.rows

import akka.stream.scaladsl.{Sink,Source}
import org.specs2.specification.Scope
import play.api.libs.json.Json
import play.api.test.{DefaultAwaitTimeout,FutureAwaits}
import scala.collection.immutable

import com.overviewdocs.metadata.{Metadata,MetadataField,MetadataFieldType,MetadataSchema}
import com.overviewdocs.models.{Document,Tag}
import test.helpers.InAppSpecification // for materializer

class DocumentsWithColumnTagsSpec extends InAppSpecification with FutureAwaits with DefaultAwaitTimeout {
  trait BaseScope extends Scope {
    val factory = com.overviewdocs.test.factories.PodoFactory
    def documents: Source[(Document,Seq[Long]), akka.NotUsed]
    val metadataSchema: MetadataSchema = MetadataSchema.empty
    val tags: Seq[Tag] = Seq()
    lazy val rows: Rows = DocumentsWithColumnTags(metadataSchema, documents, tags)
    lazy val rowList: immutable.Seq[Array[String]] = await(rows.rows.runWith(Sink.seq))
    def outHeaders = rows.headers
    def outRow1 = rowList.head
  }

  trait OneDocumentScope extends BaseScope {
    val sampleDocument: Document = factory.document(
      suppliedId="suppliedId",
      title="title",
      text="text",
      url=Some("url"),
      metadataJson=Json.obj()
    )
    val document: (Document,Seq[Long]) = (sampleDocument, Seq())
    override def documents = Source.single(document)
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
      override val document = (sampleDocument, Seq(1L, 3L))
      outRow1.drop(4) must beEqualTo(Array("", "1", "1"))
    }

    "export suppliedId" in new OneDocumentScope {
      override val document = (sampleDocument.copy(suppliedId="supplied-id"), Seq())
      outRow1(0).toString must beEqualTo("supplied-id")
    }

    "export title" in new OneDocumentScope {
      override val document = (sampleDocument.copy(title="title"), Seq())
      outRow1(1).toString must beEqualTo("title")
    }

    "export text" in new OneDocumentScope {
      override val document = (sampleDocument.copy(text="text"), Seq())
      outRow1(2).toString must beEqualTo("text")
    }

    "export url" in new OneDocumentScope {
      override val document = (sampleDocument.copy(url=Some("http://example.org")), Seq())
      outRow1(3).toString must beEqualTo("http://example.org")
    }

    "handle None as url" in new OneDocumentScope {
      override val document = (sampleDocument.copy(url=None), Seq())
      outRow1(3).toString must beEqualTo("")
    }

    "export metadata" in new OneDocumentScope {
      override val metadataSchema = MetadataSchema(1, Seq(
        MetadataField("fooField", MetadataFieldType.String),
        MetadataField("barField", MetadataFieldType.String)
      ))
      override val document = (sampleDocument.copy(metadataJson=Json.obj("fooField" -> "foo1", "barField" -> "bar1")), Seq())
      outHeaders.drop(4).take(2) must beEqualTo(Array("fooField", "barField")) // metadata before tags
      outRow1.drop(4).take(2) must beEqualTo(Array("foo1", "bar1"))
    }
  }
}
