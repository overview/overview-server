package org.overviewproject.clone

import org.overviewproject.persistence.orm.{ NodeDocument, Schema }
import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.test.DbSetup._
import org.overviewproject.test.DbSpecification
import org.overviewproject.tree.orm.{ Document, Node }
import org.overviewproject.tree.orm.DocumentType._
import org.squeryl.KeyedEntity
import org.overviewproject.persistence.DocumentSetIdGenerator

class NodeDocumentClonerSpec extends DbSpecification {

  step(setupDb)

  def createNodeDocuments(nodes: Seq[Node], documents: Seq[Document]): Seq[NodeDocument] = {
    nodes.zip(documents).map { nd =>
      Schema.nodes.insert(nd._1)
      Schema.documents.insert(nd._2)
      
      NodeDocument(nd._1.id, nd._2.id)
    }
  }
  
  def createMapping(source: Seq[KeyedEntity[Long]], clone: Seq[KeyedEntity[Long]]): Map[Long, Long] = 
    source.map(_.id).zip(clone.map(_.id)).toMap

  "NodeDocumentCloner" should {

    "clone the node_document table" in new DbTestContext {
      val documentSetId = insertDocumentSet("NodeDocumentClonerSpec")
      val cloneDocumentSetId = insertDocumentSet("ClonedNodeDocumentClonerSpec")
      val nodeIds = new DocumentSetIdGenerator(documentSetId)
      val cloneNodeIds = new DocumentSetIdGenerator(cloneDocumentSetId)
      val documentIds = new DocumentSetIdGenerator(documentSetId)
      val cloneDocumentIds = new DocumentSetIdGenerator(cloneDocumentSetId)
      
      val sourceNodes = Seq.tabulate(10)(i => Node(
        id=nodeIds.next,
        documentSetId=documentSetId,
        parentId=None,
        description="node-" + i,
        cachedSize=100,
        cachedDocumentIds=Array(),
        isLeaf=false
      ))
      val sourceDocuments = Seq.tabulate(10)(i => Document(
        id=documentIds.next,
        documentType=CsvImportDocument,
        documentSetId=documentSetId,
        text=Some("text-" + i)
      ))
      val cloneNodes = sourceNodes.map(_.copy(documentSetId = cloneDocumentSetId, id = cloneNodeIds.next))
      val cloneDocuments = sourceDocuments.map(_.copy(documentSetId = cloneDocumentSetId, id = cloneDocumentIds.next))

      val sourceNodeDocuments = createNodeDocuments(sourceNodes, sourceDocuments)
      val cloneNodeDocuments = createNodeDocuments(cloneNodes, cloneDocuments)
      
      Schema.nodeDocuments.insert(sourceNodeDocuments)
      
      NodeDocumentCloner.clone(documentSetId, cloneDocumentSetId)
      val allNodeDocuments = Schema.nodeDocuments.allRows.toSeq
      
      allNodeDocuments must haveTheSameElementsAs(sourceNodeDocuments ++ cloneNodeDocuments)
    }
  }

  step(shutdownDb)
}
