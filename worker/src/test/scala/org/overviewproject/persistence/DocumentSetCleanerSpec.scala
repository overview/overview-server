/*
 * DocumentSetCleanerSpec.scala
 *
 * Overview Project
 * Created by Jonas Karlsson, Oct 2012
 */

package org.overviewproject.persistence

import org.overviewproject.test.DbSpecification
import org.overviewproject.test.IdGenerator._
import org.overviewproject.tree.orm.{ Document, DocumentSet, Node, NodeDocument, Tree }
import org.overviewproject.tree.orm.finders.DocumentSetComponentFinder
import org.overviewproject.persistence.orm.Schema._
import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.DocumentSetCreationJob
import org.overviewproject.tree.DocumentSetCreationJobType._
import org.overviewproject.tree.orm.DocumentSetCreationJobState._

class DocumentSetCleanerSpec extends DbSpecification {
  step(setupDb)

  "DocumentSetCleaner" should {

    trait DocumentSetContext extends DbTestContext {
      var documentSet: DocumentSet = _
      var job: DocumentSetCreationJob = _
      var tree: Tree = _
      var node: Node = _
      var document: Document = _

      val cleaner = new DocumentSetCleaner

      override def setupWithDb = {
        documentSet = documentSets.insert(DocumentSet(title = "DocumentSetCleanerSpec"))
        job = documentSetCreationJobs.insert(DocumentSetCreationJob(
          documentSetId = documentSet.id,
          jobType = Recluster,
          treeTitle = Some("cluster"),
          state = InProgress
        ))
        tree = Tree(
          id = nextTreeId(documentSet.id),
          documentSetId = documentSet.id,
          jobId = job.id,
          title = "tree",
          documentCount = 100,
          lang = "en"
        )
        node = Node(nextNodeId(documentSet.id), tree.id, None, "description", 0, false)
        document = Document(documentSet.id, "description")
        val nodeDocument = NodeDocument(node.id, document.id)
        trees.insert(tree)
        nodes.insert(node)
        documents.insert(document)
        nodeDocuments.insert(nodeDocument)
      }
    }

    trait MultipleTreeContext extends DocumentSetContext {
      var otherTree: Tree = _

      override def setupWithDb = {
        super.setupWithDb
        otherTree = Tree(nextTreeId(documentSet.id), documentSet.id, job.id + 1, "other tree", 100, "en", "", "")
        trees.insert(otherTree)
      }
    }

    def findNodeWithTree(treeId: Long): Option[NodeDocument] = {
      val nodeIdQuery = from(nodes)(n =>
        where(n.treeId === treeId)
          select (n.id))

      from(nodeDocuments)(nd =>
        where(nd.nodeId in nodeIdQuery)
          select (nd)).headOption
    }

    def findNode(nodeId: Long): Option[Node] =
      from(nodes)(n =>
        where(n.id === nodeId)
          select (n)).headOption

    def findDocument(documentSetId: Long): Option[Document] =
      DocumentSetComponentFinder(documents).byDocumentSet(documentSetId).headOption

    def findTree(id: Long): Option[Tree] =
      from(trees)(t =>
        where(t.id === id)
          select (t)).headOption

    "delete node related data" in new DocumentSetContext {
      cleaner.clean(job.id, documentSet.id)

      findNodeWithTree(tree.id) must beNone
      findNode(node.id) must beNone
      findTree(tree.id) must beNone
    }

    "only delete specified tree" in new MultipleTreeContext {
      cleaner.clean(job.id, documentSet.id)

      findTree(otherTree.id) must beSome
    }

    "don't delete document related data if there are multiple trees" in new MultipleTreeContext {
      cleaner.clean(job.id, documentSet.id)

      findDocument(documentSet.id) must beSome
    }

    "delete document related data" in new DocumentSetContext {
      cleaner.clean(job.id, documentSet.id)

      val noDocument = findDocument(documentSet.id)

      noDocument must beNone
    }
  }

  step(shutdownDb)
}
