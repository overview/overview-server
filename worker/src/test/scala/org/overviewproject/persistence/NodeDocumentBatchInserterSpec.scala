/*
 * NodeDocumentBatchInserter.scala
 * 
 * Overview Project
 * Created by Jonas Karlsson, Aug 2012
 */

package org.overviewproject.persistence

import anorm._
import anorm.SqlParser._
import org.overviewproject.test.DbSpecification
import java.sql.Connection
import org.overviewproject.test.DbSetup._
import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.persistence.orm.NodeDocument
import org.overviewproject.persistence.orm.Schema

class NodeDocumentBatchInserterSpec extends DbSpecification {

  step(setupDb)

  trait DocumentsSetup extends DbTestContext {
    lazy val documentSetId = insertDocumentSet("NodeDocumentBatchInserterSpec")
    lazy val nodeId = insertNode(documentSetId, None, "description")
    val threshold = 5
    val inserter = new BatchInserter[NodeDocument](threshold, Schema.nodeDocuments)

    def insertDocumentIds(documentIds: Iterable[Long]): Unit = 
      documentIds.foreach(docId => inserter.insert(NodeDocument(nodeId, docId)))
  }

  def findNodeDocumentIds: Seq[(Long, Long)] = {
    import org.overviewproject.persistence.orm.Schema.nodeDocuments

    from(nodeDocuments)(select(_)).iterator.map(nd => (nd.nodeId, nd.documentId)).toSeq
  }

  "NodeDocumentBatchInserter" should {

    "insert data after threshold is reached" in new DocumentsSetup {
      val documentIds = insertDocuments(documentSetId, 5)

      insertDocumentIds(documentIds.take(threshold - 1))

      val beforeThresholdReached = findNodeDocumentIds
      beforeThresholdReached must be empty

      insertDocumentIds(documentIds.slice(threshold - 1, threshold))

      val afterThreshold = findNodeDocumentIds
      val expectedNodeDocuments = documentIds.map((nodeId, _))

      afterThreshold must haveTheSameElementsAs(expectedNodeDocuments)
    }

    "reset count after inserting data" in new DocumentsSetup {
      val documentIds = insertDocuments(documentSetId, 10)
      val firstBatch = documentIds.take(5)
      val secondBatch = documentIds.drop(5)

      insertDocumentIds(firstBatch)
      insertDocumentIds(secondBatch.take(2))

      val firstBatchInserted = findNodeDocumentIds
      firstBatchInserted must have size (threshold)

      insertDocumentIds(secondBatch.drop(2))

      val allInserted = findNodeDocumentIds
      val expectedNodeDocuments = documentIds.map((nodeId, _))

      allInserted must haveTheSameElementsAs(expectedNodeDocuments)

    }

    "flush remaining data when instructed to" in new DocumentsSetup {
      val documentIds = insertDocuments(documentSetId, 4)

      insertDocumentIds(documentIds)
      inserter.flush

      val flushedDocuments = findNodeDocumentIds
      val expectedNodeDocuments = documentIds.map((nodeId, _))

      flushedDocuments must haveTheSameElementsAs(expectedNodeDocuments)
    }

    "flush with no queued inserts doesn't fail" in new DocumentsSetup {

      inserter.flush
      "did not crash" must be equalTo ("did not crash")
    }
  }

  step(shutdownDb)
}
