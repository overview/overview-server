package com.overviewdocs.util

import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

import com.overviewdocs.models.DocumentSetCreationJob
import com.overviewdocs.models.DocumentSetCreationJobState._
import com.overviewdocs.models.DocumentSetCreationJobType._
import com.overviewdocs.searchindex.IndexClient

class DocumentSetCreationJobRestarterSpec extends Specification with Mockito {

  "DocumentSetCreationJobRestarter" should {

    "delete documents, index, and restart job" in new JobScope {
      jobRestarter.restart
      
      there was one(jobRestarter.mockStorage).deleteDocuments(documentSetId)
      there was one(jobRestarter.mockStorage).updateValidJob(job.copy(state = NotStarted, retryAttempts = retryAttempts + 1))
      there was one(jobRestarter.mockSearchIndex).removeDocumentSet(documentSetId)
    }

    "fail job if restart limit is reached" in new MaxRetryAttemptJobScope {
      jobRestarter.restart
      
      there was one(jobRestarter.mockStorage).updateValidJob(job.copy(state = Error, statusDescription = "max_retry_attempts"))
    }
  }

  trait JobScope extends Scope {
    val jobId = 1l
    val documentSetId = 10l
    val job = DocumentSetCreationJob(jobId, documentSetId, DocumentCloud, retryAttempts, "en", "", "", false,
      Some("user"), Some("password"), None, None, None, None, None, None, InProgress, 0.5, "", true)

    val jobRestarter = new TestDocumentSetCreationJobRestarter(job)      
    protected def retryAttempts = 0
  }
  
  trait MaxRetryAttemptJobScope extends JobScope {
    override protected def retryAttempts = Configuration.getInt("max_job_retry_attempts")    
  }

  class TestDocumentSetCreationJobRestarter(val job: DocumentSetCreationJob) extends DocumentSetCreationJobRestarter {
     override protected val storage = smartMock[DocumentStorage]
     override protected val searchIndex = smartMock[IndexClient]
     
     def mockStorage = storage
     def mockSearchIndex = searchIndex
  }
}
