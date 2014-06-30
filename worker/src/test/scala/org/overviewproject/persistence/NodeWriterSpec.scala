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
import org.overviewproject.tree.orm._
import org.overviewproject.persistence.orm.Schema._
import org.overviewproject.test.IdGenerator._
import org.overviewproject.tree.DocumentSetCreationJobType._
import org.overviewproject.tree.orm.DocumentSetCreationJobState._


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
      var writer: NodeWriter = _
      var tree: Tree = _
      var job: DocumentSetCreationJob = _

      implicit object NodeDocumentOrdering extends math.Ordering[NodeDocument] {
        override def compare(a: NodeDocument, b: NodeDocument) = {
          val c1 = a.documentId compare b.documentId
          if (c1 == 0) a.nodeId compare b.nodeId else c1
        }
      }

      override def setupWithDb = {
        documentSet = documentSets.insert(DocumentSet(title = "NodeWriterSpec"))
        job = documentSetCreationJobs.insert(
            DocumentSetCreationJob(documentSetId = documentSet.id, jobType = Recluster,
                treeTitle = Some("title"), state = NotStarted))
        tree = Tree(nextTreeId(documentSet.id), documentSet.id, job.id, "tree", 100, "en", "", "")
        writer = new NodeWriter(job.id, tree)
      }

      protected def findAllRootNodes: Iterable[Node] =
        from(nodes)(n =>
          where(n.parentId isNull)
            select (n))

      protected def findRootNode: Option[Node] = findAllRootNodes.headOption

      protected def findChildNodes(parentIds: Iterable[Long]): Seq[Node] =
        from(nodes)(n =>
          where(n.parentId in parentIds)
            select (n)).toSeq

      protected def findNodeDocuments(nodeId: Long): Seq[NodeDocument] =
        from(nodeDocuments)(nd =>
          where(nd.nodeId === nodeId)
            select (nd)).toSeq

      protected def createNode(idSet: Set[Long] = Set(), description: String = "root"): DocTreeNode = {
        val node = new DocTreeNode(idSet)
        node.description = description
        addCache(node)

        node
      }

      protected def insertDocument(documentSetId: Long): Document =
        documents.insert(Document(documentSetId = documentSetId, title = Some("title"),
          documentcloudId = Some("documentCloud ID"), id = nextDocumentId(documentSetId)))
    }

    trait MultipleTreeContext extends NodeWriterContext {
      var writer2: NodeWriter = _
      var tree2: Tree = _

      override def setupWithDb = {
        super.setupWithDb
        val job2 = documentSetCreationJobs.insert(
            DocumentSetCreationJob(documentSetId = documentSet.id, jobType = Recluster,
                treeTitle = Some("title"), state = NotStarted))
        tree2 = Tree(nextTreeId(documentSet.id), documentSet.id, job2.id, "tree2", 100, "en", "", "")
        
        writer2 = new NodeWriter(job2.id, tree2)
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
