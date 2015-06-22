/*
 * NodeWriterSpec.scala
 * 
 * Overview Project
 * Created by Jonas Karlsson, Aug 2012
 */

package org.overviewproject.persistence

import scala.collection.mutable.Set

import org.overviewproject.clustering.DocTreeNode
import org.overviewproject.database.DeprecatedDatabase
import org.overviewproject.test.DbSpecification
import org.overviewproject.test.IdGenerator
import org.overviewproject.models.{Document,DocumentSet,DocumentSetCreationJob,DocumentSetCreationJobState,DocumentSetCreationJobType,Node,NodeDocument}
import org.overviewproject.models.tables.{NodeDocuments,Nodes}

class NodeWriterSpec extends DbSpecification {
  private def addChildren(parent: DocTreeNode, description: String): Seq[DocTreeNode] = {
    val children = for (i <- 1 to 2) yield new DocTreeNode(Set())
    children.foreach(_.description = description)
    children.foreach(parent.children.add)

    children
  }

  "NodeWriter" should {

    trait NodeWriterContext extends DbScope {
      import database.api._

      val documentSet = factory.documentSet()
      val job = factory.documentSetCreationJob(
        documentSetId=documentSet.id,
        jobType=DocumentSetCreationJobType.Recluster,
        state=DocumentSetCreationJobState.InProgress,
        treeTitle=Some("title")
      )
      val writer = new NodeWriter(job.id, IdGenerator.nextTreeId(documentSet.id))

      implicit object NodeDocumentOrdering extends math.Ordering[NodeDocument] {
        override def compare(a: NodeDocument, b: NodeDocument) = {
          val c1 = a.documentId compare b.documentId
          if (c1 == 0) a.nodeId compare b.nodeId else c1
        }
      }

      protected def findAllRootNodes: Seq[Node] = blockingDatabase.seq(Nodes.filter(!_.parentId.isDefined))

      protected def findRootNode: Option[Node] = findAllRootNodes.headOption

      protected def findChildNodes(parentIds: Seq[Long]): Seq[Node] = blockingDatabase.seq {
        Nodes.filter(_.parentId inSet parentIds)
      }

      protected def findNodeDocuments(nodeId: Long): Seq[NodeDocument] = blockingDatabase.seq {
        NodeDocuments
          .filter(_.nodeId === nodeId)
          .sortBy(nd => (nd.documentId, nd.nodeId))
      }

      protected def createNode(idSet: Set[Long] = Set(), description: String = "root"): DocTreeNode = {
        val node = new DocTreeNode(idSet)
        node.description = description

        node
      }

      protected def insertDocument(documentSetId: Long): Document = {
        factory.document(id=IdGenerator.nextDocumentId(documentSetId), documentSetId=documentSet.id)
      }

      def write(dtn: DocTreeNode) = DeprecatedDatabase.inTransaction {
        writer.write(dtn)(DeprecatedDatabase.currentConnection)
      }
    }

    trait MultipleTreeContext extends NodeWriterContext {
      val job2 = factory.documentSetCreationJob(
        documentSetId=documentSet.id,
        jobType=DocumentSetCreationJobType.Recluster,
        state=DocumentSetCreationJobState.InProgress,
        treeTitle=Some("title")
      )

      val writer2 = new NodeWriter(job2.id, IdGenerator.nextTreeId(documentSet.id))

      def write2(dtn: DocTreeNode) = DeprecatedDatabase.inTransaction {
        writer2.write(dtn)(DeprecatedDatabase.currentConnection)
      }
    }

    "insert root node with description, document set, and no parent" in new NodeWriterContext {
      val description = "root"
      val root = createNode(description = description)

      write(root)

      val node = findRootNode
      node must beSome.like { case n =>
        n.description must be equalTo (description)
        n.parentId must beNone
      }
    }

    "insert child nodes" in new NodeWriterContext {
      val root = createNode()
      val childNodes = addChildren(root, "child")
      val grandChildNodes = childNodes.map(n => (n, addChildren(n, "grandchild")))

      write(root)

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

      val node = createNode(idSet)

      write(node)

      val savedNode = findRootNode

      savedNode must beSome
      val nodeId = savedNode.get.id

      val nodeDocuments = findNodeDocuments(nodeId)

      val expectedNodeDocuments = documents.map(d => NodeDocument(nodeId, d.id))

      nodeDocuments.sorted must beEqualTo(expectedNodeDocuments.sorted)
    }

    "write nodes with ids generated from documentSetId" in new NodeWriterContext {
      val node = createNode()
      write(node)
      val savedNode = findRootNode

      savedNode must beSome.like {
        case n => (n.id >> 32) must be equalTo (documentSet.id)
      }
    }

    "return a rootNodeId" in new NodeWriterContext {
      val node = createNode()
      write(node)
      val savedNode = findRootNode

      Some(writer.rootNodeId) must beEqualTo(savedNode.map(_.id))
    }

    "write nodes into second tree for the same document set" in new MultipleTreeContext {
      val root1 = createNode()
      write(root1)

      val root2 = createNode()
      write2(root2)

      findAllRootNodes.length must beEqualTo(2)
    }
  }
}
