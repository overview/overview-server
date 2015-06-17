package org.overviewproject.clone

import org.overviewproject.database.DeprecatedDatabase
import org.overviewproject.test.DbSpecification
import org.overviewproject.models.{NodeDocument,Document}
import org.overviewproject.models.tables.NodeDocuments

class NodeDocumentClonerSpec extends DbSpecification {
  "NodeDocumentCloner" should {

    "clone the node_document table" in new DbScope {
      import database.api._

      val sourceDocumentSet = factory.documentSet(id=123L)
      val cloneDocumentSet = factory.documentSet(id=124L)

      val sourceDocuments: Seq[Document] = Seq.tabulate(3) { n =>
        factory.document(documentSetId=123L, id=((123L << 32) | n))
      }

      val cloneDocuments: Seq[Document] = Seq.tabulate(3) { n =>
        factory.document(documentSetId=124L, id=((124L << 32) | n))
      }

      val sourceRootNode = factory.node(id=(123L << 32), rootId=(123L << 32), parentId=None)
      val cloneRootNode = factory.node(id=(124L << 32), rootId=(124L << 32), parentId=None)

      val sourceNodes = Seq.tabulate(2) { n => factory.node(
        id=((123L << 32) | (n + 1)),
        rootId=(123L << 32),
        parentId=Some(123L << 32)
      )}

      val cloneNodes = Seq.tabulate(2) { n => factory.node(
        id=((124L << 32) | (n + 1)),
        rootId=(124L << 32),
        parentId=Some(124L << 32)
      )}

      factory.nodeDocument(sourceRootNode.id, sourceDocuments(0).id)
      factory.nodeDocument(sourceRootNode.id, sourceDocuments(1).id)
      factory.nodeDocument(sourceRootNode.id, sourceDocuments(2).id)
      factory.nodeDocument(sourceNodes(0).id, sourceDocuments(0).id)
      factory.nodeDocument(sourceNodes(1).id, sourceDocuments(1).id)
      factory.nodeDocument(sourceNodes(1).id, sourceDocuments(2).id)

      DeprecatedDatabase.inTransaction {
        NodeDocumentCloner.clone(sourceDocumentSet.id, cloneDocumentSet.id)
      }

      val results = blockingDatabase.seq {
        NodeDocuments
          .filter(_.documentId inSet cloneDocuments.map(_.id))
          .sortBy(nd => (nd.nodeId, nd.documentId))
      }

      results must beEqualTo(Seq(
        NodeDocument(cloneRootNode.id, cloneDocuments(0).id),
        NodeDocument(cloneRootNode.id, cloneDocuments(1).id),
        NodeDocument(cloneRootNode.id, cloneDocuments(2).id),
        NodeDocument(cloneNodes(0).id, cloneDocuments(0).id),
        NodeDocument(cloneNodes(1).id, cloneDocuments(1).id),
        NodeDocument(cloneNodes(1).id, cloneDocuments(2).id)
      ))
    }
  }
}
