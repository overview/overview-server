package org.overviewproject.util

import slick.jdbc.JdbcBackend.Session

import org.overviewproject.test.{ DbSpecification, SlickClientInSession }
import org.overviewproject.models.DocumentSetCreationJobState._
import org.overviewproject.models.tables.{ DocumentSetCreationJobs, Nodes }

class ClusteringCleanerSpec extends DbSpecification {

  "ClusteringCleaner" should {
    
    "find existing tree with job id" in new TreeScope {
      cleaner.treeExists(job.id) must beTrue.await
      cleaner.treeExists(-1) must beFalse.await
    }
    
    "delete nodes" in new NodeScope {
      await(cleaner.deleteNodes(job.id))
      
      import org.overviewproject.database.Slick.simple._
      Nodes.filter(_.id === rootNode.id).firstOption(session) must beNone
    }
    
    
    "delete job" in new TreeScope {
      await(cleaner.deleteJob(job.id))
      
      import org.overviewproject.database.Slick.simple._
      val updatedJob = DocumentSetCreationJobs.filter(_.id  === job.id).firstOption(session)
      
      updatedJob must beNone
    }
  }
  
  trait JobScope extends DbScope {
    val cleaner = new TestClusteringCleaner(session)
    val documentSet = factory.documentSet()
    val job = factory.documentSetCreationJob(documentSetId = documentSet.id, treeTitle = Some("recluster"), state = jobState)

    def jobState = InProgress
  }
  
  
  trait NodeScope extends JobScope {
    val document = factory.document(documentSetId = documentSet.id)
    val rootNode = factory.node(id = 1l, rootId = 1l)
    
    factory.nodeDocument(rootNode.id, document.id)
    factory.documentSetCreationJobNode(job.id, rootNode.id)
  }

  trait TreeScope extends NodeScope {
    val tree = factory.tree(documentSetId = documentSet.id, jobId = job.id, rootNodeId = rootNode.id)
  }

  class TestClusteringCleaner(val session: Session) extends ClusteringCleaner with SlickClientInSession
}
