package models.orm.finders

import org.overviewproject.postgres.SquerylEntrypoint._
import org.specs2.mutable.Specification
import models.orm.TestSchema._
import play.api.Play.{ start, stop }
import play.api.test.FakeApplication
import helpers.DbTestContext
import org.overviewproject.tree.orm._
import org.overviewproject.tree.orm.NodeDocument

class NodeDocumentFinderSpec extends Specification {

  step(start(FakeApplication()))

  "NodeDocumentFinder" should {

    trait NodeSetup {
      def createNode(nodeId: Long, documentSetId: Long): Unit = nodes.insert(
        Node(id = nodeId,
          documentSetId = documentSetId,
          parentId = None, description = "", cachedSize = 0, cachedDocumentIds = Array.empty, isLeaf = true))

      def addDocumentsToNode(documentIds: Seq[Long], nodeId: Int): Unit =
        nodeDocuments.insert(documentIds.map(d => NodeDocument(nodeId, d)))
        
      def createDocument(id: Long, documentSetId: Long): Unit = 
        documents.insert(Document(id = id, documentSetId = documentSetId))
    }

    trait TaggedDocumentsContext extends DbTestContext with NodeSetup {
      val documentIds = 0l to 9l
      val nodeIds = 0l to 2l

      var documentSet: DocumentSet = _

      override def setupWithDb = {
        documentSet = documentSets.insertOrUpdate(DocumentSet())

        documentIds.foreach(n => createDocument(n, documentSet.id))
        nodeIds.foreach(n => createNode(n, documentSet.id))
        val tag = tags.insertOrUpdate(Tag(documentSetId = documentSet.id, name = "tag", color = "000000"))

        documentTags.insert(documentIds.take(7).map(d => DocumentTag(d, tag.id)))
        addDocumentsToNode(documentIds, 0)
        addDocumentsToNode(documentIds.take(5), 1)
        addDocumentsToNode(documentIds.drop(5), 2)

      }

    }

    trait NodesInTwoDocumentSets extends DbTestContext with NodeSetup {
      var documentSet1: DocumentSet = _
      var documentSet2: DocumentSet = _
      val documentIds1 = 100l to 109l
      val documentIds2 = 200l to 209l
      val nodeId1 = 100
      val nodeId2 = 200
      
      override def setupWithDb = {
        documentSet1 = documentSets.insertOrUpdate(DocumentSet())
        documentSet2 = documentSets.insertOrUpdate(DocumentSet())
        
        documentIds1.foreach(n => createDocument(n, documentSet1.id))
        documentIds2.foreach(n => createDocument(n, documentSet2.id))
        
        createNode(nodeId1, documentSet1.id)
        createNode(nodeId2, documentSet2.id)
        
        addDocumentsToNode(documentIds1, nodeId1)
        addDocumentsToNode(documentIds2, nodeId2)
      }
    }

    "count untagged documents in nodes" in new TaggedDocumentsContext {
      val counts = NodeDocumentFinder.byNodeIds(nodeIds).untaggedDocumentCountsByNodeId.toMap

      counts.get(0) must beSome(3)
      counts.get(1) must beNone
      counts.get(2) must beSome(3)
    }
    
    "find NodeDocuments in DocumentSet only" in new NodesInTwoDocumentSets {
      val nodeDocuments = NodeDocumentFinder.byNodeIdsInDocumentSet(documentIds1 ++ documentIds2, documentSet1.id)
      
      nodeDocuments.toSeq must haveTheSameElementsAs(documentIds1.map(NodeDocument(nodeId1, _)))
      
    }
  }

  step(stop)
}

