package org.overviewproject.util

import scala.concurrent.Future

import org.overviewproject.test.DbSpecification
import org.overviewproject.models.DocumentSetCreationJobState._
import org.overviewproject.models.tables.{ DocumentSetCreationJobs, Nodes }

class ClusteringCleanerSpec extends DbSpecification {
  "ClusteringCleaner" should {
    "find existing tree with job id" in new BaseScope {
      val tree = factory.tree(documentSetId = documentSet.id, jobId = job.id, rootNodeId = rootNode.id)
      cleaner.treeExists(job.id) must beTrue.await
      cleaner.treeExists(-1) must beFalse.await
    }

    "delete nodes" in new BaseScope {
      await(cleaner.deleteNodes(job.id))

      nodeExists(rootNode.id) must beFalse
    }

    "delete job" in new BaseScope {
      factory.tree(documentSetId = documentSet.id, jobId = job.id, rootNodeId = rootNode.id)
      await(cleaner.deleteJob(job.id))

      import databaseApi._
      blockingDatabase.length(DocumentSetCreationJobs.filter(_.id === job.id)) must beEqualTo(0)
    }
  }

  trait BaseScope extends DbScope {
    val cleaner = new ClusteringCleaner with org.overviewproject.database.DatabaseProvider
    val documentSet = factory.documentSet()
    val job = factory.documentSetCreationJob(documentSetId = documentSet.id, treeTitle = Some("recluster"), state = InProgress)

    def nodeExists(id: Long): Boolean = {
      import databaseApi._
      blockingDatabase.length(Nodes.filter(_.id === id)) > 0
    }

    val document = factory.document(documentSetId = documentSet.id)
    val rootNode = factory.node(id = 1l, rootId = 1l)
    factory.nodeDocument(rootNode.id, document.id)
    factory.documentSetCreationJobNode(job.id, rootNode.id)
  }
}
