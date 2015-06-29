/*
 * NodeDocumentBatchInserterSpec.scala
 *
 * Overview
 * Created by Jonas Karlsson, Aug 2012
 */
package org.overviewproject.persistence

import org.overviewproject.database.DeprecatedDatabase
import org.overviewproject.persistence.orm.Schema
import org.overviewproject.test.DbSpecification
import org.overviewproject.test.IdGenerator
import org.overviewproject.models.{Document,NodeDocument}
import org.overviewproject.models.tables.NodeDocuments
import org.overviewproject.tree.orm.{NodeDocument=>DeprecatedNodeDocument}

class NodeDocumentBatchInserterSpec extends DbSpecification {
  trait BaseScope extends DbScope {
    import database.api._

    val documentSet = factory.documentSet(id=123L)
    val documents: Seq[Document] = Seq.fill(5) { factory.document(documentSetId=documentSet.id) }
    val documentIds: Seq[Long] = documents.map(_.id).sorted
    val rootNodeId = 123L << 32
    val nodeIds = Seq(
      factory.node(id=rootNodeId, rootId=rootNodeId, parentId=None),
      factory.node(id=rootNodeId + 1, rootId=rootNodeId, parentId=Some(rootNodeId)),
      factory.node(id=rootNodeId + 2, rootId=rootNodeId, parentId=Some(rootNodeId))
    ).map(_.id)

    def results: Seq[NodeDocument] = blockingDatabase.seq {
      NodeDocuments
        .filter(_.documentId inSet documentIds)
        .sortBy(nd => (nd.nodeId, nd.documentId))
    }

    val inserter = new BatchInserter[DeprecatedNodeDocument](3, Schema.nodeDocuments)

    def insert(nodeId: Long, documentId: Long): Unit = DeprecatedDatabase.inTransaction {
      inserter.insert(DeprecatedNodeDocument(nodeId, documentId))
    }

    def flush = DeprecatedDatabase.inTransaction { inserter.flush }
  }

  "NodeDocumentBatchInserter" should {
    "wait to insert before the threshold is reached" in new BaseScope {
      insert(nodeIds(0), documentIds(0))
      insert(nodeIds(1), documentIds(0))
      results must beEmpty
    }

    "flush when the threshold is reached" in new BaseScope {
      insert(nodeIds(0), documentIds(0))
      insert(nodeIds(1), documentIds(0))
      insert(nodeIds(2), documentIds(0))

      results must beEqualTo(Seq(
        NodeDocument(nodeIds(0), documentIds(0)),
        NodeDocument(nodeIds(1), documentIds(0)),
        NodeDocument(nodeIds(2), documentIds(0))
      ))
    }

    "reset count after inserting data" in new BaseScope {
      insert(nodeIds(0), documentIds(0))
      insert(nodeIds(0), documentIds(1))
      insert(nodeIds(1), documentIds(0))
      insert(nodeIds(1), documentIds(1))
      insert(nodeIds(2), documentIds(0))
      insert(nodeIds(2), documentIds(1))
      insert(nodeIds(0), documentIds(2))

      results must beEqualTo(Seq(
        NodeDocument(nodeIds(0), documentIds(0)),
        NodeDocument(nodeIds(0), documentIds(1)),
        NodeDocument(nodeIds(1), documentIds(0)),
        NodeDocument(nodeIds(1), documentIds(1)),
        NodeDocument(nodeIds(2), documentIds(0)),
        NodeDocument(nodeIds(2), documentIds(1))
      ))
    }

    "flush remaining data when instructed" in new BaseScope {
      insert(nodeIds(0), documentIds(0))
      insert(nodeIds(1), documentIds(0))
      flush

      results must beEqualTo(Seq(
        NodeDocument(nodeIds(0), documentIds(0)),
        NodeDocument(nodeIds(1), documentIds(0))
      ))
    }

    "flush when empty" in new BaseScope {
      flush
      results must beEmpty
    }
  }
}
