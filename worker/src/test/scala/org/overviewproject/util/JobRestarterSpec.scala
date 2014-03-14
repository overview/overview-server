package org.overviewproject.util

import java.sql.Connection
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.overviewproject.persistence.{ DocumentSetCleaner, PersistentDocumentSetCreationJob }
import org.overviewproject.tree.orm.DocumentSetCreationJobState._
import org.overviewproject.tree.DocumentSetCreationJobType.CsvUpload


class JobRestarterSpec extends Specification with Mockito {
  implicit val unusedConnection: Connection = null

  class TestJob extends PersistentDocumentSetCreationJob {
    val id = 1l
    val jobType = CsvUpload
    val lang = "se"
    val suppliedStopWords = None
    val importantWords = None
    val documentSetId = 1L
    val documentCloudUsername: Option[String] = None
    val documentCloudPassword: Option[String] = None
    val splitDocuments: Boolean = false
    val contentsOid: Option[Long] = None
    val sourceDocumentSetId: Option[Long] = None
    val fileGroupId: Option[Long] = None
    val treeTitle: Option[String] = None
    val tagId: Option[Long] = None
    val treeDescription: Option[String] = None
    
    var state = InProgress
    var fractionComplete = 0.98
    var statusDescription: Option[String] = Some("Almost finished!")

    var updateCalled: Boolean = false

    def update = {
      updateCalled = true
    }

    def checkForCancellation {}
    def delete {}
    def observeCancellation(f: PersistentDocumentSetCreationJob => Unit) {}
  }

  "JobRestarter" should {

    "clean and restart jobs" in {
      val databaseCleaner = smartMock[DocumentSetCleaner]
      val searchIndexCleaner = smartMock[SearchIndex]

      val job = new TestJob

      val jobRestarter = new JobRestarter(databaseCleaner, searchIndexCleaner)

      jobRestarter.restart(Seq(job))

      job.state must be equalTo (NotStarted)
      job.updateCalled must beTrue 

      there was one(databaseCleaner).clean(job.id, job.documentSetId)
      there was one(searchIndexCleaner).deleteDocumentSetAliasAndDocuments(job.documentSetId)
    }
  }
}
