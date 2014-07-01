/*
 * DocumentSetCleanerSpec.scala
 *
 * Overview Project
 * Created by Jonas Karlsson, Oct 2012
 */

package org.overviewproject.persistence

import org.overviewproject.test.{ DbSpecification, IdGenerator }
import org.overviewproject.tree.orm.{ Document, DocumentSet, Node, NodeDocument, Tree }
import org.overviewproject.tree.orm.finders.DocumentSetComponentFinder
import org.overviewproject.persistence.orm.Schema
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
        documentSet = Schema.documentSets.insert(DocumentSet(title = "DocumentSetCleanerSpec"))
        job = Schema.documentSetCreationJobs.insert(DocumentSetCreationJob(
          documentSetId = documentSet.id,
          jobType = Recluster,
          treeTitle = Some("cluster"),
          state = InProgress
        ))

        val rootId = IdGenerator.nextNodeId(documentSet.id)

        node = Schema.nodes.insert(Node(rootId, rootId, None, "description", 0, true))
        tree = Schema.trees.insert(Tree(
          id = IdGenerator.nextTreeId(documentSet.id),
          documentSetId = documentSet.id,
          rootNodeId = rootId,
          jobId = job.id,
          title = "tree",
          documentCount = 100,
          lang = "en"
        ))
        document = Schema.documents.insert(Document(
          id=IdGenerator.nextDocumentId(documentSet.id),
          documentSetId=documentSet.id,
          description="description"
        ))
        Schema.nodeDocuments.insert(NodeDocument(node.id, document.id))
      }
    }

    trait MultipleTreeContext extends DocumentSetContext {
      var otherTree: Tree = _

      override def setupWithDb = {
        super.setupWithDb
        val otherNodeId = IdGenerator.nextNodeId(documentSet.id)
        val otherNode = Schema.nodes.insert(Node(otherNodeId, otherNodeId, None, "description", 0, true))
        otherTree = Schema.trees.insert(Tree(IdGenerator.nextTreeId(documentSet.id), documentSet.id, otherNodeId, job.id + 1, "other tree", 100, "en"))
      }
    }

    "delete node-related data" in new DocumentSetContext {
      cleaner.clean(job.id, documentSet.id)

      Schema.nodes.lookup(node.id) must beNone
      Schema.trees.lookup(tree.id) must beNone
    }

    "only delete specified tree" in new MultipleTreeContext {
      cleaner.clean(job.id, documentSet.id)

      Schema.trees.lookup(otherTree.id) must beSome
    }

    "don't delete document related data if there are multiple trees" in new MultipleTreeContext {
      cleaner.clean(job.id, documentSet.id)

      //Schema.documentSets.lookup(documentSet.id) must beSome
      Schema.documents.lookup(document.id) must beSome
    }

    "delete document related data if there is one tree" in new DocumentSetContext {
      cleaner.clean(job.id, documentSet.id)

      //Schema.documentSets.lookup(documentSet.id) must beNone
      Schema.documents.lookup(document.id) must beNone
    }
  }

  step(shutdownDb)
}
