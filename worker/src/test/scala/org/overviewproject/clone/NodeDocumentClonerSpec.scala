package org.overviewproject.clone

import org.overviewproject.persistence.orm.Schema
import org.overviewproject.test.DbSpecification
import org.overviewproject.tree.orm.{ Document, DocumentSet, Node, NodeDocument, Tree }
import org.squeryl.KeyedEntity
import org.overviewproject.persistence.{ DocumentSetIdGenerator, NodeIdGenerator }
import org.overviewproject.persistence.orm.Schema.documentSets

class NodeDocumentClonerSpec extends DbSpecification {

  step(setupDb)

  implicit object NodeDocumentOrdering extends math.Ordering[NodeDocument] {
    override def compare(a: NodeDocument, b: NodeDocument) = {
      val c1 = a.documentId compare b.documentId
      if (c1 == 0) a.nodeId compare b.nodeId else c1
    }
  }

  def createNodeDocuments(nodes: Seq[Node], documents: Seq[Document]): Seq[NodeDocument] = {
    import org.overviewproject.postgres.SquerylEntrypoint._
    nodes.zip(documents).map { nd =>
      Schema.nodes.insert(nd._1)
      Schema.documents.insert(nd._2)

      NodeDocument(nd._1.id, nd._2.id)
    }
  }

  def createMapping(source: Seq[KeyedEntity[Long]], clone: Seq[KeyedEntity[Long]]): Map[Long, Long] =
    source.map(_.id).zip(clone.map(_.id)).toMap

  def insertDocumentSet(title: String): Long = {
    import org.overviewproject.postgres.SquerylEntrypoint._
    val documentSet = documentSets.insertOrUpdate(DocumentSet(title = title))
    documentSet.id
  }

  "NodeDocumentCloner" should {

    "clone the node_document table" in new DbTestContext {
      import org.overviewproject.postgres.SquerylEntrypoint._
      val documentSetId = insertDocumentSet("NodeDocumentClonerSpec")
      val cloneDocumentSetId = insertDocumentSet("ClonedNodeDocumentClonerSpec")
      val treeId = new DocumentSetIdGenerator(documentSetId).next
      val cloneTreeId = new DocumentSetIdGenerator(cloneDocumentSetId).next
      val nodeIds = new NodeIdGenerator(treeId)
      val cloneNodeIds = new NodeIdGenerator(cloneTreeId)
      val documentIds = new DocumentSetIdGenerator(documentSetId)
      val cloneDocumentIds = new DocumentSetIdGenerator(cloneDocumentSetId)

      val sourceNodes = Seq.tabulate(4)(i => Node(
        id=nodeIds.next,
        rootId=nodeIds.rootId,
        parentId=None,
        description="node-" + i,
        cachedSize=100,
        isLeaf=false
      ))
      val sourceDocuments = Seq.tabulate(4)(i => Document(
        id=documentIds.next,
        documentSetId=documentSetId,
        text=Some("text-" + i)
      ))
      val cloneNodes = sourceNodes.map(_.copy(
        id = cloneNodeIds.next,
        rootId = cloneNodeIds.rootId
      ))
      val cloneDocuments = sourceDocuments.map(_.copy(documentSetId = cloneDocumentSetId, id = cloneDocumentIds.next))

      val sourceNodeDocuments = createNodeDocuments(sourceNodes, sourceDocuments)
      val cloneNodeDocuments = createNodeDocuments(cloneNodes, cloneDocuments)

      Schema.nodeDocuments.insert(sourceNodeDocuments)

      NodeDocumentCloner.clone(documentSetId, cloneDocumentSetId)
      val allNodeDocuments = Schema.nodeDocuments.allRows.toSeq

      allNodeDocuments.sorted must beEqualTo((sourceNodeDocuments ++ cloneNodeDocuments).sorted)
    }
  }

  step(shutdownDb)
}
