/*
 * NodeDocumentBatchInserterSpec.scala
 *
 * Overview Project
 * Created by Jonas Karlsson, Aug 2012
 */

package org.overviewproject.persistence

import org.overviewproject.persistence.orm.Schema
import org.overviewproject.test.DbSpecification
import org.overviewproject.test.IdGenerator
import org.overviewproject.tree.orm.{ Document, DocumentSet, Node, NodeDocument }

class NodeDocumentBatchInserterSpec extends DbSpecification {
  step(setupDb)

  trait DocumentsSetup extends DbTestContext {
    import org.overviewproject.postgres.SquerylEntrypoint._
    var documentSet: DocumentSet = _
    var node: Node = _

    val threshold = 5
    val inserter = new BatchInserter[NodeDocument](threshold, Schema.nodeDocuments)

    def insertDocumentIds(documentIds: Iterable[Long]): Unit =
      documentIds.foreach(docId => inserter.insert(NodeDocument(node.id, docId)))

    override def setupWithDb = {
      documentSet = Schema.documentSets.insert(DocumentSet(title = "NodeDocumentBatchInserterSpec"))
      val nodeId = IdGenerator.nextNodeId(documentSet.id)
      node = Schema.nodes.insert(Node(nodeId, nodeId, None, "description", 1, true))
    }

    protected def findNodeDocumentIds: Seq[(Long, Long)] =
      from(Schema.nodeDocuments)(select(_)).iterator.map(nd => (nd.nodeId, nd.documentId)).toSeq

    protected def insertDocuments(documentSetId: Long, numDocuments: Int): Seq[Long] = {
      val ids = Seq.fill(numDocuments)(IdGenerator.nextDocumentId(documentSetId))

      val docs = ids.map { id => Document(id = id, documentSetId = documentSetId) }
      Schema.documents.insert(docs)

      ids
    }

  }

  "NodeDocumentBatchInserter" should {
    "insert data after threshold is reached" in new DocumentsSetup {
      val documentIds = insertDocuments(documentSet.id, 5)

      insertDocumentIds(documentIds.take(threshold - 1))

      val beforeThresholdReached = findNodeDocumentIds
      beforeThresholdReached must be empty

      insertDocumentIds(documentIds.slice(threshold - 1, threshold))

      val afterThreshold = findNodeDocumentIds
      val expectedNodeDocuments = documentIds.map((node.id, _))

      afterThreshold must containTheSameElementsAs(expectedNodeDocuments)
    }

    "reset count after inserting data" in new DocumentsSetup {
      val documentIds = insertDocuments(documentSet.id, 10)
      val firstBatch = documentIds.take(5)
      val secondBatch = documentIds.drop(5)

      insertDocumentIds(firstBatch)
      insertDocumentIds(secondBatch.take(2))

      val firstBatchInserted = findNodeDocumentIds
      firstBatchInserted must have size (threshold)

      insertDocumentIds(secondBatch.drop(2))

      val allInserted = findNodeDocumentIds
      val expectedNodeDocuments = documentIds.map((node.id, _))

      allInserted must containTheSameElementsAs(expectedNodeDocuments)

    }

    "flush remaining data when instructed to" in new DocumentsSetup {
      val documentIds = insertDocuments(documentSet.id, 4)

      insertDocumentIds(documentIds)
      inserter.flush

      val flushedDocuments = findNodeDocumentIds
      val expectedNodeDocuments = documentIds.map((node.id, _))

      flushedDocuments must containTheSameElementsAs(expectedNodeDocuments)
    }

    "flush with no queued inserts doesn't fail" in new DocumentsSetup {

      inserter.flush
      "did not crash" must be equalTo ("did not crash")
    }
  }

  step(shutdownDb)
}
