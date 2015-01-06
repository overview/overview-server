package org.overviewproject.util

import org.overviewproject.test.{ SlickClientInSession, SlickSpecification }
import org.overviewproject.models.DocumentSetCreationJobState._
import org.overviewproject.models.tables.{ DocumentSetCreationJobs, Nodes }
import org.overviewproject.database.Slick.simple._

class ClusteringCleanerSpec extends SlickSpecification {

  "ClusteringCleaner" should {
    
    "update job retry parameters" in new JobScope {
      val jobUpdate = job.copy(state = NotStarted, retryAttempts = 5, statusDescription = "updated")
      cleaner.updateValidJob(jobUpdate)
      
      val updatedJob = DocumentSetCreationJobs.filter(_.id  === job.id)
      
      updatedJob.firstOption must beSome(jobUpdate)
    }
    
    "not update cancelled jobs" in new CancelledJobScope {
      val jobUpdate = job.copy(state = NotStarted, retryAttempts = 5, statusDescription = "updated")
      cleaner.updateValidJob(jobUpdate)
      
      val updatedJob = DocumentSetCreationJobs.filter(_.id  === job.id)
      
      updatedJob.firstOption must beSome(job)
    }
    
    "find existing tree with job id" in new TreeScope {
      cleaner.treeExists(job.id) must beTrue.await
      cleaner.treeExists(-1) must beFalse.await
    }
    
    "delete nodes" in new NodeScope {
      await(cleaner.deleteNodes(job.id))
      
      Nodes.filter(_.id === rootNode.id).firstOption must beNone
    }
    
    "delete DocumentSetCreationJobNode" in {
      todo
    }
    
    "delete job" in {
      todo
    }
  }
  
  trait JobScope extends DbScope {
    val cleaner = new TestClusteringCleaner
    val documentSet = factory.documentSet()
    val job = factory.documentSetCreationJob(documentSetId = documentSet.id, treeTitle = Some("recluster"), state = jobState)

    def jobState = InProgress
  }
  
  trait CancelledJobScope extends JobScope {
    override def jobState = Cancelled
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
  
  class TestClusteringCleaner(implicit val session: Session) extends ClusteringCleaner with SlickClientInSession
}