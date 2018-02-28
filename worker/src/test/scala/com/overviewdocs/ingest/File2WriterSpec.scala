package com.overviewdocs.ingest

import org.specs2.mock.Mockito
import play.api.libs.json.{Json,JsObject}

import com.overviewdocs.blobstorage.BlobStorage
import com.overviewdocs.models.File2
import com.overviewdocs.models.tables.{Documents,DocumentProcessingErrors}
import com.overviewdocs.ingest.models.ProcessedFile2
import com.overviewdocs.test.DbSpecification

class File2WriterSpec extends DbSpecification with Mockito {
  case class DbDocument(
    // Just the columns we test
    id: Long,
    title: String,
    pageNumber: Option[Int],
    text: String,
    metadataJson: JsObject,
    isFromOcr: Boolean
  )
  object DbDocument {
    def fromRow(
      id: Long,
      title: Option[String],
      pageNumber: Option[Int],
      text: Option[String],
      metadataJson: Option[JsObject],
      isFromOcr: Option[Boolean]
    ) = DbDocument(id, title.get, pageNumber, text.get, metadataJson.get, isFromOcr.get)
  }

  case class DbError(
    message: String,
    file2Id: Option[Long]
  )

  trait BaseScope extends DbScope {
    implicit val ec = scala.concurrent.ExecutionContext.global

    factory.documentSet(id=123L)

    lazy val dbDocumentsQuery = {
      import database.api._

      Compiled { documentSetId: Rep[Long] =>
        Documents
          .filter(_.documentSetId === documentSetId)
          .sortBy(_.id)
          .map(d => (d.id, d.title, d.pageNumber, d.text, d.metadataJson, d.isFromOcr))
      }
    }

    lazy val dbErrorsQuery = {
      import database.api._

      Compiled { documentSetId: Rep[Long] =>
        DocumentProcessingErrors
          .filter(_.documentSetId === documentSetId)
          .sortBy(_.id)
          .map(d => (d.message, d.file2Id))
      }
    }

    def processedFile2(documentSetId: Long, filename: String, text: String, metadataJson: JsObject, nChildren: Int = 0, processingError: Option[String] = None): ProcessedFile2 = {
      val dbFile2 = factory.file2(
        filename=filename,
        text=Some(text),
        metadata=File2.Metadata(metadataJson),
        nChildren=Some(nChildren),
        processingError=processingError
      )

      ProcessedFile2(dbFile2.id, documentSetId, None, nChildren, 0)
    }

    val mockBlobStorage = mock[BlobStorage]
    val maxNTextChars = 100
    lazy val subject = new File2Writer(database, mockBlobStorage, maxNTextChars)

    def dbDocuments(documentSetId: Long): Vector[DbDocument] = {
      blockingDatabase.seq(dbDocumentsQuery(documentSetId))
        .map((DbDocument.fromRow _).tupled)
    }

    def dbErrors(documentSetId: Long): Vector[DbError] = {
      blockingDatabase.seq(dbErrorsQuery(documentSetId))
        .map((DbError.apply _).tupled)
    }

    def ingest(file2s: Vector[ProcessedFile2]): Unit = await(subject.ingestBatch(file2s))
  }

  "File2Writer" should {
    "#ingestBatch" should {
      "create Documents" in new BaseScope {
        ingest(Vector(
          processedFile2(123L, "doc1.pdf", "doc1", Json.obj("foo" -> "bar")),
          processedFile2(123L, "doc2.pdf", "doc2", Json.obj("foo" -> "baz"))
        ))

        dbDocuments(123L) must beEqualTo(Vector(
          DbDocument((123L << 32) | 0, "doc1.pdf", None, "doc1", Json.obj("foo" -> "bar"), false),
          DbDocument((123L << 32) | 1, "doc2.pdf", None, "doc2", Json.obj("foo" -> "baz"), false)
        ))
      }

      "create Documents in different DocumentSets" in new BaseScope {
        factory.documentSet(124L)

        ingest(Vector(
          processedFile2(123L, "doc1.pdf", "doc1", Json.obj("foo" -> "bar")),
          processedFile2(124L, "doc2.pdf", "doc2", Json.obj("foo" -> "baz"))
        ))

        dbDocuments(123L) must beEqualTo(Vector(
          DbDocument((123L << 32) | 0, "doc1.pdf", None, "doc1", Json.obj("foo" -> "bar"), false),
        ))
        dbDocuments(124L) must beEqualTo(Vector(
          DbDocument((124L << 32) | 0, "doc2.pdf", None, "doc2", Json.obj("foo" -> "baz"), false)
        ))
      }

      "allocate sequential document IDs" in new BaseScope {
        factory.document(id=(123L << 32) | 10, documentSetId=123L)

        ingest(Vector(
          processedFile2(123L, "doc1.pdf", "doc1", Json.obj("foo" -> "bar")),
        ))

        dbDocuments(123L).map(_.id) must beEqualTo(Vector((123L << 32) | 10, (123L << 32) | 11))
      }

      "ignore already-existing documents" in new BaseScope {
        val file2 = processedFile2(123L, "doc1.pdf", "doc1", Json.obj("foo" -> "bar"))
        factory.document(id=(123L << 32) | 1, documentSetId=123L, title="existing", file2Id=Some(file2.id))

        ingest(Vector(file2))
        dbDocuments(123L).map(_.title) must beEqualTo(Vector("existing"))
      }

      "create a document_processing_error" in new BaseScope {
        val input1 = processedFile2(123L, "doc1.pdf", "doc1", Json.obj(), 1, Some("error-foo"))
        ingest(Vector(input1))

        dbErrors(123L) must beEqualTo(Vector(
          DbError("error-foo", Some(input1.id))
        ))
      }

      "ignore existing document_processing_errors" in new BaseScope {
        val input1 = processedFile2(123L, "doc1.pdf", "doc1", Json.obj(), 1, Some("error-foo"))
        factory.documentProcessingError(documentSetId=123L, file2Id=Some(input1.id), message="existing")
        ingest(Vector(input1))
        dbErrors(123L).map(_.message) must beEqualTo(Vector("existing"))
      }

      "ignore parent documents" in new BaseScope {
        ingest(Vector(processedFile2(123L, "doc1.pdf", "doc1", Json.obj(), 1)))
        dbDocuments(123L) must beEmpty
      }

      "set is_ocr" in new BaseScope {
        ingest(Vector(
          processedFile2(123L, "doc1.pdf", "doc1", Json.obj()),
          processedFile2(123L, "doc2.pdf", "doc2", Json.obj("isFromOcr" -> false)),
          processedFile2(123L, "doc3.pdf", "doc3", Json.obj("isFromOcr" -> true)),
        ))

        dbDocuments(123L).map(_.isFromOcr) must beEqualTo(Vector(false, false, true))
      }

      "set page_number" in new BaseScope {
        ingest(Vector(
          processedFile2(123L, "doc1.pdf", "doc1", Json.obj()),
          processedFile2(123L, "doc2.pdf", "doc2", Json.obj("pageNumber" -> 2)),
          processedFile2(123L, "doc3.pdf", "doc3", Json.obj("pageNumber" -> "4")),
          processedFile2(123L, "doc4.pdf", "doc4", Json.obj("pageNumber" -> Json.obj("foo" -> "bar")))
        ))

        dbDocuments(123L).map(_.pageNumber) must beEqualTo(Vector(None, Some(2), None, None))
      }

      "not crash with \u0000 in metadata" in new BaseScope {
        ingest(Vector(
          processedFile2(123L, "doc1.pdf", "doc1", Json.obj("foo" -> "asdf\u0000blsdf")),
        ))
        true must beTrue
      }
    }
  }
}
