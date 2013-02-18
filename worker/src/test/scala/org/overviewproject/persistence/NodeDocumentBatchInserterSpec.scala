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

class NodeDocumentBatchInserterSpec extends DbSpecification {

  step(setupDb)

  trait DocumentsSetup extends DbTestContext {
    lazy val documentSetId = insertDocumentSet("NodeDocumentBatchInserterSpec")
    lazy val nodeId = insertNode(documentSetId, None, "description")
  }

  def findNodeDocumentIds: Seq[(Long, Long)] = {
    import org.overviewproject.persistence.Schema.nodeDocuments

    from(nodeDocuments)(select(_)).iterator.map(nd => (nd.nodeId, nd.documentId)).toSeq
  }

  "NodeDocumentBatchInserter" should {

    "insert data after threshold is reached" in new DocumentsSetup {
      val documentIds = insertDocuments(documentSetId, 5)
      val threshold = 5
      val inserter = new NodeDocumentBatchInserter(threshold)

      documentIds.take(threshold - 1).foreach(inserter.insert(nodeId, _))

      val beforeThresholdReached = findNodeDocumentIds
      beforeThresholdReached must be empty

      documentIds.slice(threshold - 1, threshold).foreach(inserter.insert(nodeId, _))

      val afterThreshold = findNodeDocumentIds
      val expectedNodeDocuments = documentIds.map((nodeId, _))

      afterThreshold must haveTheSameElementsAs(expectedNodeDocuments)
    }

    "reset count after inserting data" in new DocumentsSetup {
      val documentIds = insertDocuments(documentSetId, 10)
      val firstBatch = documentIds.take(5)
      val secondBatch = documentIds.drop(5)
      val threshold = 5
      val inserter = new NodeDocumentBatchInserter(threshold)

      firstBatch.foreach(inserter.insert(nodeId, _))
      secondBatch.take(2).foreach(inserter.insert(nodeId, _))

      val firstBatchInserted = findNodeDocumentIds
      firstBatchInserted must have size (threshold)

      secondBatch.drop(2).foreach(inserter.insert(nodeId, _))

      val allInserted = findNodeDocumentIds
      val expectedNodeDocuments = documentIds.map((nodeId, _))

      allInserted must haveTheSameElementsAs(expectedNodeDocuments)

    }

    "flush remaining data when instructed to" in new DocumentsSetup {
      val documentIds = insertDocuments(documentSetId, 4)
      val threshold = 5
      val inserter = new NodeDocumentBatchInserter(threshold)

      documentIds.foreach(inserter.insert(nodeId, _))
      inserter.flush

      val flushedDocuments = findNodeDocumentIds
      val expectedNodeDocuments = documentIds.map((nodeId, _))

      flushedDocuments must haveTheSameElementsAs(expectedNodeDocuments)
    }

    "flush with no queued inserts doesn't fail" in new DocumentsSetup {
      val threshold = 5
      val inserter = new NodeDocumentBatchInserter(threshold)

      inserter.flush
      "did not crash" must be equalTo ("did not crash")
    }
  }

  step(shutdownDb)
}
