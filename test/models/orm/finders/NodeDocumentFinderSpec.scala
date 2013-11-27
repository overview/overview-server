package models.orm.finders

import org.overviewproject.postgres.SquerylEntrypoint._
import org.specs2.mutable.Specification
import models.orm.TestSchema._
import play.api.Play.{ start, stop }
import play.api.test.FakeApplication
import helpers.DbTestContext
import org.overviewproject.tree.orm._
import models.orm.NodeDocument

class NodeDocumentFinderSpec extends Specification {

  step(start(FakeApplication()))

  "NodeDocumentFinder" should {

    trait TaggedDocumentsContext extends DbTestContext {
      val documentIds = 0l to 9l
      val nodeIds = 0l to 2l

      var documentSet: DocumentSet = _

      override def setupWithDb = {
        documentSet = documentSets.insertOrUpdate(DocumentSet())

        documentIds.foreach(n => documents.insert(Document(id = n, documentSetId = documentSet.id)))
        nodeIds.foreach(n => createNode(n, documentSet.id))
        val tag = tags.insertOrUpdate(Tag(documentSetId = documentSet.id, name = "tag", color = "000000"))

        documentTags.insert(documentIds.take(7).map(d => DocumentTag(d, tag.id)))
        addDocumentsToNode(documentIds, 0)
        addDocumentsToNode(documentIds.take(5), 1)
        addDocumentsToNode(documentIds.drop(5), 2)

      }

      private def createNode(nodeId: Long, documentSetId: Long): Unit = nodes.insert(
        Node(id = nodeId,
          documentSetId = documentSetId,
          parentId = None, description = "", cachedSize = 0, cachedDocumentIds = Array.empty, isLeaf = true))

      private def addDocumentsToNode(documentIds: Seq[Long], nodeId: Int): Unit =
        nodeDocuments.insert(documentIds.map(d => NodeDocument(nodeId, d)))

    }

    
    "count untagged documents in nodes" in new TaggedDocumentsContext {
      val counts = NodeDocumentFinder.byNodeIds(nodeIds).untaggedDocumentCountsByNodeId.toMap
      
      counts.get(0) must beSome(3)
      counts.get(1) must beNone
      counts.get(2) must beSome(3)
    }
  }

  step(stop)
}

