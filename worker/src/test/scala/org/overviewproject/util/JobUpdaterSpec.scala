package org.overviewproject.util

import org.overviewproject.test.DbSpecification
import org.overviewproject.models.DocumentSetCreationJob
import org.overviewproject.models.DocumentSetCreationJobState._
import org.overviewproject.models.tables.DocumentSetCreationJobs

class JobUpdaterSpec extends DbSpecification {

  "JobUpdater" should {
    "update job retry parameters" in new JobScope {
      val jobUpdate = job.copy(statusDescription = "updated")
      await(updater.updateValidJob(jobUpdate))
      findJob(job.id) must beSome(jobUpdate)
    }

    "not update cancelled jobs" in new JobScope {
      override def jobState = Cancelled
      val jobUpdate = job.copy(statusDescription = "updated")
      await(updater.updateValidJob(jobUpdate))
      findJob(job.id) must beSome(job)
    }

    "only update specified job" in new JobScope {
      val job2 = factory.documentSetCreationJob(documentSetId = documentSet.id)
      val jobUpdate = job.copy(statusDescription = "updated")
      await(updater.updateValidJob(jobUpdate))
      findJob(job2.id) must beSome(job2)
    }
  }

  trait JobScope extends DbScope {
    val updater = JobUpdater

    val documentSet = factory.documentSet()
    def jobState = InProgress
    lazy val job = factory.documentSetCreationJob(documentSetId = documentSet.id, treeTitle = Some("recluster"), state = jobState)

    def findJob(id: Long): Option[DocumentSetCreationJob] = {
      import databaseApi._
      blockingDatabase.option(DocumentSetCreationJobs.filter(_.id  === id))
    }
  }
}
