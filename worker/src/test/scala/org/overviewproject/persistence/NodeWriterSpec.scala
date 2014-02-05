/*
 * NodeWriterSpec.scala
 * 
 * Overview Project
 * Created by Jonas Karlsson, Aug 2012
 */

package org.overviewproject.persistence

import scala.collection.mutable.Set
import org.overviewproject.clustering.{ DocTreeNode, DocumentIdCache }
import org.overviewproject.test.DbSpecification
import org.specs2.execute.PendingUntilFixed
import org.overviewproject.tree.orm.{ Document, DocumentSet, Node, NodeDocument, Tree }
import org.overviewproject.persistence.orm.Schema.{ documents, documentSets, nodes, nodeDocuments, trees }
import org.overviewproject.test.IdGenerator._

class NodeWriterSpec extends DbSpecification {

  step(setupDb)

  private def addChildren(parent: DocTreeNode, description: String): Seq[DocTreeNode] = {
    val children = for (i <- 1 to 2) yield new DocTreeNode(Set())
    children.foreach(addCache)
    children.foreach(_.description = description)
    children.foreach(parent.children.add)

    children
  }

  // add a dummy cache that's not used for anything
  private def addCache(node: DocTreeNode) {
    node.documentIdCache = new DocumentIdCache(10, Array[Long](1l, 2l, 3l, 4l))
  }

  "NodeWriter" should {

    trait NodeWriterContext extends DbTestContext {
      import org.overviewproject.postgres.SquerylEntrypoint._

      var documentSet: DocumentSet = _
      var tree: Tree = _
      var writer: NodeWriter = _

      implicit object NodeDocumentOrdering extends math.Ordering[NodeDocument] {
        override def compare(a: NodeDocument, b: NodeDocument) = {
          val c1 = a.documentId compare b.documentId
          if (c1 == 0) a.nodeId compare b.nodeId else c1
        }
      }

      override def setupWithDb = {
        documentSet = documentSets.insert(DocumentSet(title = "NodeWriterSpec"))
        tree = trees.insert(Tree(nextTreeId(documentSet.id), documentSet.id, "tree", 100, "en", "", ""))

        writer = new NodeWriter(documentSet.id, tree.id)
      }

      protected def findRootNode: Option[Node] =
        from(nodes)(n =>
          where(n.parentId isNull)
            select (n)).headOption

      protected def findChildNodes(parentIds: Iterable[Long]): Seq[Node] =
        from(nodes)(n =>
          where(n.parentId in parentIds)
            select (n)).toSeq

      protected def findNodeDocuments(nodeId: Long): Seq[NodeDocument] =
        from(nodeDocuments)(nd =>
          where(nd.nodeId === nodeId)
            select (nd)).toSeq

      protected def insertDocument(documentSetId: Long): Document =
        documents.insert(Document(documentSetId = documentSetId, title = Some("title"),
          documentcloudId = Some("documentCloud ID"), id = nextDocumentId(documentSetId)))
    }

    "insert root node with description, document set, and no parent" in new NodeWriterContext {
      val root = new DocTreeNode(Set())
      val description = "description"
      root.description = description
      addCache(root)

      writer.write(root)

      val node = findRootNode
      node must beSome.like {
        case n =>
          n.treeId must beEqualTo(tree.id)
          n.description must be equalTo (description)
          n.parentId must beNone
      }
    }

    "insert child nodes" in new NodeWriterContext {
      val root = new DocTreeNode(Set())
      root.description = "root"
      addCache(root)
      val childNodes = addChildren(root, "child")
      val grandChildNodes = childNodes.map(n => (n, addChildren(n, "grandchild")))

      writer.write(root)

      val savedRoot = findRootNode
      savedRoot must beSome

      val savedChildren = findChildNodes(Seq(savedRoot.get.id))
      savedChildren must have size (2)
      savedChildren.map(_.description must be equalTo ("child"))

      val childIds = savedChildren.map(_.id)
      val savedGrandChildren = findChildNodes(childIds)

      savedGrandChildren must have size (4)
    }

    "insert document into node_document table" in new NodeWriterContext {
      val documents = Seq.fill(5)(insertDocument(documentSet.id))
      val idSet = Set(documents.map(_.id): _*)

      val node = new DocTreeNode(idSet)
      node.description = "node"
      addCache(node)

      writer.write(node)

      val savedNode = findRootNode

      savedNode must beSome
      val nodeId = savedNode.get.id

      val nodeDocuments = findNodeDocuments(nodeId)

      val expectedNodeDocuments = documents.map(d => NodeDocument(nodeId, d.id))

      nodeDocuments.sorted must beEqualTo(expectedNodeDocuments.sorted)
    }

    "write nodes with ids generated from documentSetId" in new NodeWriterContext {
      val node = new DocTreeNode(Set())
      addCache(node)
      writer.write(node)
      val savedNode = findRootNode

      savedNode must beSome.like {
        case n => (n.id >> 32) must be equalTo (documentSet.id)
      }
    }
  }

  step(shutdownDb)
}
