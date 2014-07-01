/*
 * NodeWriterSpec.scala
 * 
 * Overview Project
 * Created by Jonas Karlsson, Aug 2012
 */

package org.overviewproject.persistence

import scala.collection.mutable.Set
import org.overviewproject.clustering.DocTreeNode
import org.overviewproject.test.DbSpecification
import org.specs2.execute.PendingUntilFixed
import org.overviewproject.tree.DocumentSetCreationJobType
import org.overviewproject.tree.orm.{Document, DocumentSet, DocumentSetCreationJob, DocumentSetCreationJobState, Node, NodeDocument}
import org.overviewproject.persistence.orm.Schema
import org.overviewproject.test.IdGenerator

class NodeWriterSpec extends DbSpecification {

  step(setupDb)

  private def addChildren(parent: DocTreeNode, description: String): Seq[DocTreeNode] = {
    val children = for (i <- 1 to 2) yield new DocTreeNode(Set())
    children.foreach(_.description = description)
    children.foreach(parent.children.add)

    children
  }

  "NodeWriter" should {

    trait NodeWriterContext extends DbTestContext {
      import org.overviewproject.postgres.SquerylEntrypoint._

      var documentSet: DocumentSet = _
      var job: DocumentSetCreationJob = _
      var writer: NodeWriter = _

      implicit object NodeDocumentOrdering extends math.Ordering[NodeDocument] {
        override def compare(a: NodeDocument, b: NodeDocument) = {
          val c1 = a.documentId compare b.documentId
          if (c1 == 0) a.nodeId compare b.nodeId else c1
        }
      }

      override def setupWithDb = {
        documentSet = Schema.documentSets.insert(DocumentSet(title = "NodeWriterSpec"))
        job = Schema.documentSetCreationJobs.insert(DocumentSetCreationJob(
          documentSetId=documentSet.id,
          jobType=DocumentSetCreationJobType.Recluster,
          state=DocumentSetCreationJobState.InProgress,
          treeTitle=Some("title")
        ))
        writer = new NodeWriter(job.id, IdGenerator.nextTreeId(documentSet.id))
      }

      protected def findAllRootNodes: Iterable[Node] =
        from(Schema.nodes)(n =>
          where(n.parentId isNull)
            select (n))

      protected def findRootNode: Option[Node] = findAllRootNodes.headOption

      protected def findChildNodes(parentIds: Iterable[Long]): Seq[Node] =
        from(Schema.nodes)(n =>
          where(n.parentId in parentIds)
            select (n)).toSeq

      protected def findNodeDocuments(nodeId: Long): Seq[NodeDocument] =
        from(Schema.nodeDocuments)(nd =>
          where(nd.nodeId === nodeId)
            select (nd)).toSeq

      protected def createNode(idSet: Set[Long] = Set(), description: String = "root"): DocTreeNode = {
        val node = new DocTreeNode(idSet)
        node.description = description

        node
      }

      protected def insertDocument(documentSetId: Long): Document =
        Schema.documents.insert(Document(documentSetId = documentSetId, title = Some("title"),
          documentcloudId = Some("documentCloud ID"), id = IdGenerator.nextDocumentId(documentSetId)))
    }

    trait MultipleTreeContext extends NodeWriterContext {
      var writer2: NodeWriter = _

      override def setupWithDb = {
        super.setupWithDb
        val job2 = Schema.documentSetCreationJobs.insert(DocumentSetCreationJob(
          documentSetId=documentSet.id,
          jobType=DocumentSetCreationJobType.Recluster,
          state=DocumentSetCreationJobState.InProgress,
          treeTitle=Some("title")
        ))
        writer2 = new NodeWriter(job2.id, IdGenerator.nextTreeId(documentSet.id))
      }
    }

    "insert root node with description, document set, and no parent" in new NodeWriterContext {
      val description = "root"
      val root = createNode(description = description)

      writer.write(root)

      val node = findRootNode
      node must beSome.like {
        case n =>
          n.description must be equalTo (description)
          n.parentId must beNone
      }
    }

    "insert child nodes" in new NodeWriterContext {
      val root = createNode()
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

      val node = createNode(idSet)

      writer.write(node)

      val savedNode = findRootNode

      savedNode must beSome
      val nodeId = savedNode.get.id

      val nodeDocuments = findNodeDocuments(nodeId)

      val expectedNodeDocuments = documents.map(d => NodeDocument(nodeId, d.id))

      nodeDocuments.sorted must beEqualTo(expectedNodeDocuments.sorted)
    }

    "write nodes with ids generated from documentSetId" in new NodeWriterContext {
      val node = createNode()
      writer.write(node)
      val savedNode = findRootNode

      savedNode must beSome.like {
        case n => (n.id >> 32) must be equalTo (documentSet.id)
      }
    }

    "return a rootNodeId" in new NodeWriterContext {
      val node = createNode()
      writer.write(node)
      val savedNode = findRootNode

      Some(writer.rootNodeId) must beEqualTo(savedNode.map(_.id))
    }

    "write nodes into second tree for the same document set" in new MultipleTreeContext {
      val root1 = createNode()
      writer.write(root1)

      val root2 = createNode()
      writer2.write(root2)

      findAllRootNodes must haveSize(2)
    }
  }

  step(shutdownDb)
}
