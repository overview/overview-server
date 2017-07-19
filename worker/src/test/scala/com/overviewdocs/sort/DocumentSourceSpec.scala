package com.overviewdocs.sort

import akka.stream.scaladsl.Sink
import akka.stream.ActorMaterializer
import play.api.libs.json.Json

import com.overviewdocs.test.{ActorSystemContext,DbSpecification}
import com.overviewdocs.test.factories.DbFactory
import com.overviewdocs.util.AwaitMethod

class DocumentSourceSpec extends DbSpecification with AwaitMethod {
  sequential

  trait BaseScope extends DbScope with ActorSystemContext {
    implicit lazy val mat: ActorMaterializer = ActorMaterializer.create(system)

    val subject = new DocumentSource(database, 2)

    val documentSet = factory.documentSet()
    val document1 = factory.document(documentSetId=documentSet.id, id=documentSet.id << 32, metadataJson=Json.obj("foo" -> "bar"))
    val document2 = factory.document(documentSetId=documentSet.id, id=(documentSet.id << 32) | 1, metadataJson=Json.obj("foo" -> "bar 2"))
    setupDocumentIdArray(documentSet.id, Array(document2.id, document1.id))

    def getRecordSource(fieldName: String): RecordSource = {
      await(subject.recordSourceByMetadata(documentSet.id, fieldName))
    }

    def getRecords(fieldName: String): Seq[Record] = {
      await(getRecordSource(fieldName).records.runWith(Sink.seq))
    }

    def setupDocumentIdArray(documentSetId: Long, ids: Array[Long]): Unit = {
      import database.api._
      blockingDatabase.runUnit(sqlu"""
        UPDATE document_set
        SET sorted_document_ids = '{#${ids.mkString(",")}}'
        WHERE id = ${documentSetId}
      """)
    }
  }

  "DocumentSource" should {
    "#recordSourceByMetadata" should {
      "get records from document_set.sorted_document_ids" in new BaseScope {
        val recordSource = getRecordSource("foo")
        recordSource.nRecords must beEqualTo(2)
        await(recordSource.records.runWith(Sink.seq)).map(_.id) must containTheSameElementsAs(Seq(1, 0))
      }

      "paginate" in new BaseScope {
        val document3 = factory.document(documentSetId=documentSet.id, id=(documentSet.id << 32) | 2)
        setupDocumentIdArray(documentSet.id, Array(document2.id, document1.id, document3.id))
        getRecords("foo").map(_.id) must containTheSameElementsAs(Seq(0, 1, 2))
      }

      "default to empty collationKey" in new BaseScope {
        // zero-length String gives 3-byte collation keys. Go figure.
        getRecords("invalidField").map(_.collationKey.size) must beEqualTo(Seq(3, 3))
      }

      "gather collationKey" in new BaseScope {
        // We're not testing collation per se: we're just testing that a longer
        // String gets a longer collation key (and that collation keys aren't
        // reused).
        getRecords("foo").map(_.collationKey.size).sorted must beEqualTo(Seq(8, 10))
      }
    }
  }
}
