package com.overviewdocs.ingest

import akka.util.ByteString
import akka.stream.scaladsl.Source
import java.nio.file.{Files,Path}
import java.time.Instant
import org.specs2.mock.Mockito
import play.api.libs.json.{Json,JsObject}
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{Future,Promise,blocking}

import com.overviewdocs.blobstorage.{BlobBucketId,BlobStorage}
import com.overviewdocs.models.{BlobStorageRef,File2}
import com.overviewdocs.models.tables.{Documents,DocumentProcessingErrors,File2s}
import com.overviewdocs.ingest.model.{BlobStorageRefWithSha1,CreatedFile2,WrittenFile2,ProcessedFile2,ResumedFileGroupJob,FileGroupProgressState,ProgressPiece}
import com.overviewdocs.test.{ActorSystemContext,DbSpecification}

class File2WriterSpec extends DbSpecification with Mockito {
  "File2Writer" should {
    "#createChild, #delete, #writeBlob, #writeBlobStorageRef, #writeThumbnail, #writeText" should {
      trait BaseScope extends DbScope with ActorSystemContext {
        implicit val ec = system.dispatcher

        override def await[T](future: Future[T]): T = {
          // wait only 2s
          blocking(scala.concurrent.Await.result(future, scala.concurrent.duration.Duration("2s")))
        }

        val documentSet = factory.documentSet(id=123L)
        val fileGroup = factory.fileGroup(
          addToDocumentSetId=Some(documentSet.id),
          lang=Some("en"),
          splitDocuments=Some(false),
          ocr=Some(true),
          nFiles=Some(5),
          nBytes=Some(20000L),
          nFilesProcessed=Some(0),
          nBytesProcessed=Some(0L)
        )
        val fileGroupJob = ResumedFileGroupJob(
          fileGroup,
          new FileGroupProgressState(fileGroup, 0, 0L, Instant.now, _ => (), Promise[akka.Done]()),
          () => ()
        )

        val parentBlob: BlobStorageRef = BlobStorageRef("foo", 10L)
        val parentBlobSha1: Array[Byte] = Array(1, 2, 3).map(_.toByte)
        val parentThumbnailOpt: Option[BlobStorageRef] = None
        val parentTextOpt: Option[String] = None
        lazy val dbParent = factory.file2(
          rootFile2Id=None,
          parentFile2Id=None,
          filename="parent.pdf",
          contentType="application/parent",
          languageCode="en",
          blob=Some(parentBlob),
          blobSha1=parentBlobSha1,
          thumbnailBlob=parentThumbnailOpt,
          text=parentTextOpt,
          createdAt=Instant.now,
          writtenAt=Some(Instant.now),
          processedAt=None,
          nChildren=None,
          processingError=None,
          ingestedAt=None
        )

        val parent = WrittenFile2(
          dbParent.id,
          fileGroupJob,
          ProgressPiece.Null,
          dbParent.rootFile2Id,
          dbParent.parentFile2Id,
          dbParent.filename,
          dbParent.contentType,
          dbParent.languageCode,
          dbParent.metadata.jsObject,
          dbParent.wantOcr,
          dbParent.wantSplitByPage,
          BlobStorageRefWithSha1(parentBlob, parentBlobSha1)
        )

        lazy val lookupFile2Compiled = {
          import database.api._
          Compiled { id: Rep[Long] => File2s.filter(_.id === id) }
        }
        def lookupFile2(id: Long) = blockingDatabase.option(lookupFile2Compiled(id))

        class MockBlobStorage extends BlobStorage {
          override protected val config = null
          override protected val strategyFactory = null

          val deletes = ArrayBuffer.empty[String]
          val creates = ArrayBuffer.empty[(BlobBucketId,Path,Array[Byte])]

          override def create(bucket: BlobBucketId, dataPath: Path): Future[String] = {
            val bytes = Files.readAllBytes(dataPath)
            creates.+=((bucket, dataPath, bytes))
            Future.successful(creates.length.toString)
          }

          override def delete(location: String): Future[Unit] = {
            deletes.+=(location)
            Future.unit
          }
        }

        val mockBlobStorage = new MockBlobStorage
        val maxNCharsPerDocument = 100
        lazy val subject = new File2Writer(database, mockBlobStorage, maxNCharsPerDocument)
      }

      "after #createChild" should {
        trait CreatedChildScope extends BaseScope {
          var child = await(subject.createChild(
            parent,
            0,
            "child.pdf",
            "application/child",
            "fr",
            Json.obj("foo" -> "bar"),
            true,
            false
          ))

          def dbChildOpt = lookupFile2(child.id)
        }

        "create a child" in new CreatedChildScope {
          child.fileGroupJob must beEqualTo(fileGroupJob)
          child.rootId must beSome(dbParent.id)
          child.parentId must beSome(dbParent.id)
          child.blobOpt must beNone
          child.ownsBlob must beEqualTo(false)
          child.thumbnailLocationOpt must beNone
          child.ownsThumbnail must beEqualTo(false)

          dbChildOpt must beSome
          val dbChild = dbChildOpt.get
          dbChild.filename must beEqualTo("child.pdf")
          dbChild.metadata must beEqualTo(File2.Metadata(Json.obj("foo" -> "bar")))
          dbChild.wantOcr must beEqualTo(true)
          dbChild.wantSplitByPage must beEqualTo(false)
        }

        "#writeBlob() with a blob" in new CreatedChildScope {
          val blob = Source.single(ByteString("foobar"))
          val blobSha1 = "8843d7f92416211de9ebb963ff4ce28125932878".grouped(2).map(s => Integer.parseInt(s, 16).toByte).toArray
          val child2 = await(subject.writeBlob(child, blob))

          mockBlobStorage.creates.length must beEqualTo(1)
          mockBlobStorage.creates(0)._1 must beEqualTo(BlobBucketId.FileView)
          mockBlobStorage.creates(0)._3 must beEqualTo("foobar".getBytes("utf-8"))
          Files.exists(mockBlobStorage.creates(0)._2) must beFalse // the file passed to BlobStorage is deleted

          child2.blobOpt.map(_.ref) must beSome(BlobStorageRef("1", 6L))
          child2.blobOpt.map(_.sha1.toVector) must beSome(blobSha1.toVector)
          child2.ownsBlob must beTrue
          dbChildOpt.map(_.blob) must beSome(child2.blobOpt.map(_.ref))
          dbChildOpt.map(_.blobSha1.toVector) must beSome(child2.blobOpt.get.sha1.toVector)
        }

        "#writeBlobStorageRef with parent blob" in new CreatedChildScope {
          val child2 = await(subject.writeBlobStorageRef(child, parent.blob))

          mockBlobStorage.creates.length must beEqualTo(0)
          child2.blobOpt.map(_.ref) must beSome(parentBlob)
          child2.blobOpt.map(_.sha1.toVector) must beSome(parentBlobSha1.toVector)
          child2.ownsBlob must beFalse
          val dbChild = dbChildOpt.get
          dbChild.blob must beSome(parentBlob)
          dbChild.blobSha1.toVector must beEqualTo(parentBlobSha1.toVector)
        }

        "ignore a second #writeBlob" in new CreatedChildScope {
          val blob1 = Source.single(ByteString("foobar"))
          val blob1Sha1 = "8843d7f92416211de9ebb963ff4ce28125932878".grouped(2).map(s => Integer.parseInt(s, 16).toByte).toArray

          var readBlob2 = Promise[Boolean]()
          val blob2 = Source.single(ByteString("foobar"))
            .watchTermination()((_, done) => { readBlob2.success(true); done })
          val blob2Sha1 = "8843d7f92416211de9ebb963ff4ce28125932878".grouped(2).map(s => Integer.parseInt(s, 16).toByte).toArray

          val child2 = await(subject.writeBlob(child, blob1))
          val child3 = await(subject.writeBlob(child2, blob2))

          mockBlobStorage.creates.length must beEqualTo(1) // only one write
          // Even though we ignore the input, we need to consume it. If we
          // don't and the user is submitting the blob through HTTP POST, the
          // rest of the POST request will never be read -- meaning the entire
          // HTTP session will stall.
          await(readBlob2.future) must beEqualTo(true)
        }

        "ignore a second #writeBlobStorageRef" in new CreatedChildScope {
          val blob1 = Source.single(ByteString("foobar"))
          val blob1Sha1 = "8843d7f92416211de9ebb963ff4ce28125932878".grouped(2).map(s => Integer.parseInt(s, 16).toByte).toArray
          val child2 = await(subject.writeBlob(child, blob1))
          val child3 = await(subject.writeBlobStorageRef(child2, BlobStorageRefWithSha1(parentBlob, parentBlobSha1)))

          child3.blobOpt must beEqualTo(child2.blobOpt)
        }

        "#writeThumbnail()" in new CreatedChildScope {
          val blob = Source.single(ByteString("foobar"))
          val child2 = await(subject.writeThumbnail(child, "application/test", blob))

          child2.thumbnailLocationOpt must beSome("1")
          child2.ownsThumbnail must beTrue
          mockBlobStorage.creates.length must beEqualTo(1)
          mockBlobStorage.creates(0)._1 must beEqualTo(BlobBucketId.FileView)
          mockBlobStorage.creates(0)._3 must beEqualTo("foobar".getBytes("utf-8"))
          Files.exists(mockBlobStorage.creates(0)._2) must beFalse // the file passed to BlobStorage is deleted
          val dbChild = dbChildOpt.get
          dbChild.thumbnailBlob must beSome(BlobStorageRef("1", 6L))
          dbChild.thumbnailContentType must beSome("application/test")
        }

        "ignore a second #writeThumbnail()" in new CreatedChildScope {
          val blob1 = Source.single(ByteString("foobar"))
          var readBlob2 = Promise[Boolean]()
          val blob2 = Source.single(ByteString("foobar"))
            .watchTermination()((_, done) => { readBlob2.success(true); done })

          val child2 = await(subject.writeThumbnail(child, "application/test1", blob1))
          val child3 = await(subject.writeThumbnail(child2, "application/test2", blob2))

          child3.thumbnailLocationOpt must beSome("1")
          child3.ownsThumbnail must beTrue
          dbChildOpt.get.thumbnailContentType must beSome("application/test1")

          mockBlobStorage.creates.length must beEqualTo(1) // only one write
          // Even though we ignore the input, we need to consume it. If we
          // don't and the user is submitting the blob through HTTP POST, the
          // rest of the POST request will never be read -- meaning the entire
          // HTTP session will stall.
          await(readBlob2.future) must beEqualTo(true)
        }

        "delete thumbnail and blob from BlobStorage" in new CreatedChildScope {
          val child2 = await(subject.writeBlob(child, Source.single(ByteString("foo"))))
          val child3 = await(subject.writeThumbnail(child2, "image/png", Source.single(ByteString("bar"))))

          await(subject.delete(child3))

          dbChildOpt must beNone
          mockBlobStorage.deletes.toVector must beEqualTo(Vector("1", "2"))
        }

        "not delete unowned blobs from BlobStorage" in new CreatedChildScope {
          val child2 = await(subject.writeBlobStorageRef(child, parent.blob))
          val child3 = await(subject.writeThumbnail(child2, "image/png", Source.single(ByteString("bar"))))

          await(subject.delete(child3))

          dbChildOpt must beNone
          mockBlobStorage.deletes.toVector must beEqualTo(Vector("1"))
        }

        "delete even when there are no blobs" in new CreatedChildScope {
          await(subject.delete(child))
          dbChildOpt must beNone
          mockBlobStorage.deletes must beEmpty
        }
      }
    }

    "#ingestBatch" should {
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

        val documentSet = factory.documentSet(id=123L)
        val fileGroup = factory.fileGroup(
          addToDocumentSetId=Some(documentSet.id),
          lang=Some("en"),
          splitDocuments=Some(false),
          ocr=Some(true),
          nFiles=Some(5),
          nBytes=Some(20000L),
          nFilesProcessed=Some(0),
          nBytesProcessed=Some(0L)
        )
        val fileGroupJob = ResumedFileGroupJob(
          fileGroup,
          new FileGroupProgressState(fileGroup, 0, 0L, Instant.now, _ => (), Promise[akka.Done]()),
          () => ()
        )

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

        def processedFile2(fileGroupJob: ResumedFileGroupJob, filename: String, text: String, metadataJson: JsObject, nChildren: Int = 0, processingError: Option[String] = None): ProcessedFile2 = {
          val dbFile2 = factory.file2(
            filename=filename,
            text=Some(text),
            metadata=File2.Metadata(metadataJson),
            nChildren=Some(nChildren),
            processingError=processingError
          )

          ProcessedFile2(dbFile2.id, fileGroupJob, None, nChildren, 0)
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

      "create Documents" in new BaseScope {
        ingest(Vector(
          processedFile2(fileGroupJob, "doc1.pdf", "doc1", Json.obj("foo" -> "bar")),
          processedFile2(fileGroupJob, "doc2.pdf", "doc2", Json.obj("foo" -> "baz"))
        ))

        dbDocuments(123L) must beEqualTo(Vector(
          DbDocument((123L << 32) | 0, "doc1.pdf", None, "doc1", Json.obj("foo" -> "bar"), false),
          DbDocument((123L << 32) | 1, "doc2.pdf", None, "doc2", Json.obj("foo" -> "baz"), false)
        ))
      }

      "create Documents in different DocumentSets" in new BaseScope {
        val documentSet2 = factory.documentSet(124L)
        val fileGroup2 = factory.fileGroup(
          addToDocumentSetId=Some(documentSet2.id),
          lang=Some("en"),
          splitDocuments=Some(false),
          ocr=Some(true),
          nFiles=Some(5),
          nBytes=Some(20000L),
          nFilesProcessed=Some(0),
          nBytesProcessed=Some(0L)
        )
        val fileGroupJob2 = ResumedFileGroupJob(
          fileGroup2,
          new FileGroupProgressState(fileGroup2, 0, 0L, Instant.now, _ => (), Promise[akka.Done]()),
          () => ()
        )

        ingest(Vector(
          processedFile2(fileGroupJob, "doc1.pdf", "doc1", Json.obj("foo" -> "bar")),
          processedFile2(fileGroupJob2, "doc2.pdf", "doc2", Json.obj("foo" -> "baz"))
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
          processedFile2(fileGroupJob, "doc1.pdf", "doc1", Json.obj("foo" -> "bar")),
        ))

        dbDocuments(123L).map(_.id) must beEqualTo(Vector((123L << 32) | 10, (123L << 32) | 11))
      }

      "ignore already-existing documents" in new BaseScope {
        val file2 = processedFile2(fileGroupJob, "doc1.pdf", "doc1", Json.obj("foo" -> "bar"))
        factory.document(id=(123L << 32) | 1, documentSetId=123L, title="existing", file2Id=Some(file2.id))

        ingest(Vector(file2))
        dbDocuments(123L).map(_.title) must beEqualTo(Vector("existing"))
      }

      "create a document_processing_error" in new BaseScope {
        val input1 = processedFile2(fileGroupJob, "doc1.pdf", "doc1", Json.obj(), 1, Some("error-foo"))
        ingest(Vector(input1))

        dbErrors(123L) must beEqualTo(Vector(
          DbError("error-foo", Some(input1.id))
        ))
      }

      "ignore existing document_processing_errors" in new BaseScope {
        val input1 = processedFile2(fileGroupJob, "doc1.pdf", "doc1", Json.obj(), 1, Some("error-foo"))
        factory.documentProcessingError(documentSetId=123L, file2Id=Some(input1.id), message="existing")
        ingest(Vector(input1))
        dbErrors(123L).map(_.message) must beEqualTo(Vector("existing"))
      }

      "ignore parent documents" in new BaseScope {
        ingest(Vector(processedFile2(fileGroupJob, "doc1.pdf", "doc1", Json.obj(), 1)))
        dbDocuments(123L) must beEmpty
      }

      "set is_ocr" in new BaseScope {
        ingest(Vector(
          processedFile2(fileGroupJob, "doc1.pdf", "doc1", Json.obj()),
          processedFile2(fileGroupJob, "doc2.pdf", "doc2", Json.obj("isFromOcr" -> false)),
          processedFile2(fileGroupJob, "doc3.pdf", "doc3", Json.obj("isFromOcr" -> true)),
        ))

        dbDocuments(123L).map(_.isFromOcr) must beEqualTo(Vector(false, false, true))
      }

      "set page_number" in new BaseScope {
        ingest(Vector(
          processedFile2(fileGroupJob, "doc1.pdf", "doc1", Json.obj()),
          processedFile2(fileGroupJob, "doc2.pdf", "doc2", Json.obj("pageNumber" -> 2)),
          processedFile2(fileGroupJob, "doc3.pdf", "doc3", Json.obj("pageNumber" -> "4")),
          processedFile2(fileGroupJob, "doc4.pdf", "doc4", Json.obj("pageNumber" -> Json.obj("foo" -> "bar")))
        ))

        dbDocuments(123L).map(_.pageNumber) must beEqualTo(Vector(None, Some(2), None, None))
      }

      "not crash with \u0000 in metadata" in new BaseScope {
        ingest(Vector(
          processedFile2(fileGroupJob, "doc1.pdf", "doc1", Json.obj("foo" -> "asdf\u0000blsdf")),
        ))
        true must beTrue
      }
    }
  }
}
