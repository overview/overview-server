package org.overviewproject.database

import org.specs2.mock.Mockito
import slick.jdbc.JdbcBackend.Session

import org.overviewproject.blobstorage.BlobStorage
import org.overviewproject.models.DocumentSetCreationJob
import org.overviewproject.models.DocumentSetCreationJobState
import org.overviewproject.models.DocumentSetCreationJobState._
import org.overviewproject.models.DocumentSetCreationJobType._
import org.overviewproject.models.tables.{ DocumentSetCreationJobs, DocumentSetCreationJobMappings }
import org.overviewproject.test.SlickSpecification
import org.overviewproject.test.SlickClientInSession

class DocumentSetCreationJobDeleterSpec extends SlickSpecification with Mockito {

  "DocumentSetCreationJobDeleter" should {

    "delete a job" in new JobScope {
      await { deleter.deleteByDocumentSet(documentSet.id) }

      import org.overviewproject.database.Slick.simple._
      DocumentSetCreationJobs.list(session) must beEmpty
    }

    "delete uploaded csv" in new CsvImportJobScope {
      await { deleter.deleteByDocumentSet(documentSet.id) }

      import org.overviewproject.database.Slick.simple._
      DocumentSetCreationJobs.list(session) must beEmpty

      there was one(deleter.mockBlobStorage).delete(s"pglo:$oid")
    }

    "delete job with state" in new CancelledJobScope {
      await { deleter.delete(cancelledJob.id) }

      import org.overviewproject.database.Slick.simple._
      DocumentSetCreationJobs.filter(_.state === Cancelled.value).list(session) must beEmpty
      DocumentSetCreationJobs.list(session) must haveSize(1)
    }
  }

  trait JobScope extends DbScope {
    val deleter = new TestDocumentSetDeleter(session)

    val documentSet = factory.documentSet()
    val job = createJob

    def createJob: DocumentSetCreationJob =
      factory.documentSetCreationJob(documentSetId = documentSet.id, jobType = Recluster, treeTitle = Some("tree"))
  }

  trait CsvImportJobScope extends JobScope {
    def oid = 1l

    override def createJob =
      factory.documentSetCreationJob(documentSetId = documentSet.id, jobType = CsvUpload, contentsOid = Some(oid))
  }

  trait CancelledJobScope extends JobScope with DocumentSetCreationJobMappings {
    val cancelledJob = factory.documentSetCreationJob(documentSetId = documentSet.id, jobType = Recluster, treeTitle = Some("cancelled"),
      state = Cancelled)

  }
  class TestDocumentSetDeleter(val session: Session) extends DocumentSetCreationJobDeleter
      with SlickClientInSession {

    override protected val blobStorage = mock[BlobStorage]

    def mockBlobStorage = blobStorage
  }

}
