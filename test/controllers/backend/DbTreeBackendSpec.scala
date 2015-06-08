package controllers.backend

import org.overviewproject.models.{Node,NodeDocument,Tree}
import org.overviewproject.models.tables.{Nodes,NodeDocuments,Trees}

class DbTreeBackendSpec extends DbBackendSpecification {
  trait BaseScope extends DbScope {
    val backend = new DbTreeBackend with org.overviewproject.database.DatabaseProvider

    def findTree(id: Long) = {
      import org.overviewproject.database.Slick.simple._
      Trees.filter(_.id === id).firstOption(session)
    }

    def findNode(id: Long) = {
      import org.overviewproject.database.Slick.simple._
      Nodes.filter(_.id === id).firstOption(session)
    }

    def findNodeDocument(nodeId: Long, documentId: Long) = {
      import org.overviewproject.database.Slick.simple._
      NodeDocuments
        .filter(_.nodeId === nodeId)
        .filter(_.documentId === documentId)
        .firstOption(session)
    }
  }

  "DbTreeBackend" should {
    "#destroy" should {
      trait DestroyScope extends BaseScope {
        val documentSet = factory.documentSet()
        val rootNodeId = 123L
        val rootNode = factory.node(id=rootNodeId, rootId=rootNodeId, parentId=None)
        val subNode = factory.node(rootId=rootNodeId, parentId=Some(rootNodeId))
        val tree = factory.tree(documentSetId=documentSet.id, rootNodeId=rootNodeId)

        def destroy = await(backend.destroy(tree.id))
      }

      "destroy the Tree" in new DestroyScope {
        destroy
        findTree(tree.id) must beNone
      }

      "not destroy another Tree" in new DestroyScope {
        val rootNodeId2 = 124L
        val rootNode2 = factory.node(id=rootNodeId2, rootId=rootNodeId2, parentId=None)
        val tree2 = factory.tree(documentSetId=documentSet.id, rootNodeId=rootNodeId2)

        destroy

        findTree(tree2.id) must beSome
      }

      "destroy the Tree's Nodes" in new DestroyScope {
        destroy
        findNode(rootNodeId) must beNone
        findNode(subNode.id) must beNone
      }

      "not destroy another Tree's Nodes" in new DestroyScope {
        val rootNodeId2 = 124L
        val rootNode2 = factory.node(id=rootNodeId2, rootId=rootNodeId2, parentId=None)
        val tree2 = factory.tree(documentSetId=documentSet.id, rootNodeId=rootNodeId2)

        destroy

        findNode(rootNodeId2) must beSome
      }

      "destroy NodeDocuments" in new DestroyScope {
        val doc1 = factory.document(documentSetId=documentSet.id)
        val doc2 = factory.document(documentSetId=documentSet.id)
        val nd1 = factory.nodeDocument(rootNodeId, doc1.id)
        val nd2 = factory.nodeDocument(rootNodeId, doc2.id)
        val nd3 = factory.nodeDocument(subNode.id, doc1.id)

        destroy

        findNodeDocument(nd1.nodeId, nd1.documentId) must beNone
        findNodeDocument(nd2.nodeId, nd2.documentId) must beNone
        findNodeDocument(nd3.nodeId, nd3.documentId) must beNone
      }

      "not destroy another Tree's NodeDocuments" in new DestroyScope {
        val rootNodeId2 = 124L
        val rootNode2 = factory.node(id=rootNodeId2, rootId=rootNodeId2, parentId=None)
        val tree2 = factory.tree(documentSetId=documentSet.id, rootNodeId=rootNodeId2)
        val doc1 = factory.document(documentSetId=documentSet.id)
        val doc2 = factory.document(documentSetId=documentSet.id)
        val nd1 = factory.nodeDocument(rootNodeId2, doc1.id)
        val nd2 = factory.nodeDocument(rootNodeId2, doc2.id)

        destroy

        findNodeDocument(nd1.nodeId, nd1.documentId) must beSome
        findNodeDocument(nd2.nodeId, nd2.documentId) must beSome
      }
    }

    "#update" should {
      trait UpdateScope extends BaseScope {
        val documentSet = factory.documentSet()
        val rootNodeId = 123L
        val rootNode = factory.node(id=rootNodeId, rootId=rootNodeId, parentId=None)
        val tree = factory.tree(documentSetId=documentSet.id, rootNodeId=rootNodeId, title="original-title")

        val treeId = tree.id
        val attributes = Tree.UpdateAttributes(title="foobar")
        def update: Option[Tree] = await(backend.update(treeId, attributes))
      }

      "update title" in new UpdateScope {
        update

        findTree(tree.id).map(_.title) must beSome("foobar")
      }

      "not update anything else" in new UpdateScope {
        update

        val dbTree = findTree(tree.id)
        dbTree.map(_.documentSetId) must beSome(tree.documentSetId)
        dbTree.map(_.rootNodeId) must beSome(tree.rootNodeId)
        dbTree.map(_.jobId) must beSome(tree.jobId)
        dbTree.map(_.documentCount) must beSome(tree.documentCount)
        dbTree.map(_.lang) must beSome(tree.lang)
        dbTree.map(_.description) must beSome(tree.description)
        dbTree.map(_.suppliedStopWords) must beSome(tree.suppliedStopWords)
        dbTree.map(_.importantWords) must beSome(tree.importantWords)
        dbTree.map(_.createdAt) must beSome(tree.createdAt)
      }

      "return the updated Tree" in new UpdateScope {
        update must beEqualTo(findTree(tree.id))
      }

      "not update a missing Tree" in new UpdateScope {
        override val treeId = tree.id + 1

        update must beNone
        findTree(tree.id).map(_.title) must beSome("original-title")
      }
    }
  }
}
