package models.orm.finders

import org.specs2.mutable.Specification
import play.api.Play.{ start, stop }
import play.api.test.FakeApplication

import helpers.DbTestContext
import models.orm.TestSchema._
import org.overviewproject.tree.orm._

class NodeDocumentFinderSpec extends Specification {

  step(start(FakeApplication()))

  "NodeDocumentFinder" should {

    trait NodeSetup {
      import org.overviewproject.postgres.SquerylEntrypoint._

      def createNode(nodeId: Long, rootId: Long): Unit = nodes.insert(Node(
        id = nodeId,
        rootId = rootId,
        parentId = None,
        description = "",
        cachedSize = 0,
        isLeaf = true
      ))

      def addDocumentsToNode(documentIds: Seq[Long], nodeId: Int): Unit =
        nodeDocuments.insert(documentIds.map(d => NodeDocument(nodeId, d)))

      def createDocument(id: Long, documentSetId: Long): Unit =
        documents.insert(Document(id = id, documentSetId = documentSetId))

      def createTree(documentSetId: Long, rootNodeId: Long): Tree = {
        val tree = Tree(documentSetId, documentSetId, rootNodeId, 0L, "title", 100, "en")
        trees.insert(tree)

        tree
      }

    }

    trait TaggedDocumentsContext extends DbTestContext with NodeSetup {
      val documentIds = 0l to 9l
      val nodeIds = 0l to 2l

      var documentSet: DocumentSet = _

      override def setupWithDb = {
        import org.overviewproject.postgres.SquerylEntrypoint._

        documentSet = documentSets.insertOrUpdate(DocumentSet())
        documentIds.foreach(n => createDocument(n, documentSet.id))
        nodeIds.foreach(n => createNode(n, nodeIds.head))
        val tree = createTree(documentSet.id, nodeIds.head)
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
      var tree1: Tree = _
      val documentIds1 = 100l to 109l
      val documentIds2 = 200l to 209l
      val nodeId1 = 100
      val nodeId2 = 200

      override def setupWithDb = {
        import org.overviewproject.postgres.SquerylEntrypoint._

        documentSet1 = documentSets.insertOrUpdate(DocumentSet())
        documentSet2 = documentSets.insertOrUpdate(DocumentSet())

        createNode(nodeId1, nodeId1)
        createNode(nodeId2, nodeId2)

        tree1 = createTree(documentSet1.id, nodeId1)
        val tree2 = createTree(documentSet2.id, nodeId2)

        documentIds1.foreach(n => createDocument(n, documentSet1.id))
        documentIds2.foreach(n => createDocument(n, documentSet2.id))

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

    "find NodeDocuments in Tree only" in new NodesInTwoDocumentSets {
      val nd = NodeDocumentFinder.byNodeIdsInTree(documentIds1 ++ documentIds2, tree1.id).toSeq

      nd.map(_.nodeId).distinct must beEqualTo(Seq(nodeId1))
      nd.map(_.documentId).sorted must beEqualTo(documentIds1)
    }
  }

  step(stop)
}

