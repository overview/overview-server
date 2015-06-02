package org.overviewproject.util

import scala.concurrent.Future

import org.overviewproject.test.{ DbSpecification, SlickClientInSession }
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

      nodeExists(rootNode.id) must beFalse.await
    }
    
    
    "delete job" in new BaseScope {
      factory.tree(documentSetId = documentSet.id, jobId = job.id, rootNodeId = rootNode.id)
      await(cleaner.deleteJob(job.id))
      
      import org.overviewproject.database.Slick.simple._
      val updatedJob = DocumentSetCreationJobs.filter(_.id  === job.id).firstOption(session)
      
      updatedJob must beNone
    }
  }

  trait BaseScope extends DbScope {
    val cleaner = new TestClusteringCleaner
    val documentSet = factory.documentSet()
    val job = factory.documentSetCreationJob(documentSetId = documentSet.id, treeTitle = Some("recluster"), state = jobState)

    def nodeExists(id: Long): Future[Boolean] = {
      import org.overviewproject.database.Slick.api._
      slickDb.run(Nodes.filter(_.id === id).length.result).map(_ > 0)
    }

    def jobState = InProgress

    val document = factory.document(documentSetId = documentSet.id)
    val rootNode = factory.node(id = 1l, rootId = 1l)
    factory.nodeDocument(rootNode.id, document.id)
    factory.documentSetCreationJobNode(job.id, rootNode.id)
  }

  class TestClusteringCleaner extends ClusteringCleaner with SlickClientInSession
}
