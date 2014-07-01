/*
 * NodeDocumentBatchInserterSpec.scala
 * 
 * Overview Project
 * Created by Jonas Karlsson, Aug 2012
 */

package org.overviewproject.persistence

import org.overviewproject.persistence.orm.Schema._
import org.overviewproject.test.DbSpecification
import org.overviewproject.test.IdGenerator._
import org.overviewproject.tree.orm.{ Document, DocumentSet, Node, NodeDocument, Tree }

class NodeDocumentBatchInserterSpec extends DbSpecification {

  step(setupDb)

  trait DocumentsSetup extends DbTestContext {
    import org.overviewproject.postgres.SquerylEntrypoint._
    var documentSet: DocumentSet = _
    var node: Node = _

    val threshold = 5
    val inserter = new BatchInserter[NodeDocument](threshold, nodeDocuments)

    def insertDocumentIds(documentIds: Iterable[Long]): Unit =
      documentIds.foreach(docId => inserter.insert(NodeDocument(node.id, docId)))

    override def setupWithDb = {
      documentSet = documentSets.insert(DocumentSet(title = "NodeDocumentBatchInserterSpec"))
      val tree = Tree(nextTreeId(documentSet.id), documentSet.id, 0L, "tree", 100, "en", "", "")
      node = Node(nextNodeId(documentSet.id), tree.id, None, "description", 1, false)

      trees.insert(tree)
      nodes.insert(node)
    }

    protected def findNodeDocumentIds: Seq[(Long, Long)] =
      from(nodeDocuments)(select(_)).iterator.map(nd => (nd.nodeId, nd.documentId)).toSeq

    protected def insertDocuments(documentSetId: Long, numDocuments: Int): Seq[Long] = {
      val ids = Seq.fill(numDocuments)(nextDocumentId(documentSetId))

      val docs = ids.map { id => Document(id = id, documentSetId = documentSetId) }
      documents.insert(docs)

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
