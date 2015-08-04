/*
 * PersistentDocumentSetCreationJobSpec.scala
 *
 * Overview
 * Created by Jonas Karlsson, Aug 2012
 */
package com.overviewdocs.persistence

import com.overviewdocs.models.{DocumentSet,DocumentSetCreationJob,DocumentSetCreationJobState=>State,DocumentSetCreationJobType=>Type}
import com.overviewdocs.models.tables.DocumentSetCreationJobs
import com.overviewdocs.tree.{DocumentSetCreationJobType=>DeprecatedType}
import com.overviewdocs.tree.orm.{DocumentSetCreationJobState=>DeprecatedState}
import com.overviewdocs.test.DbSpecification

class PersistentDocumentSetCreationJobSpec extends DbSpecification {
  "PersistentDocumentSetCreationJob" should {
    trait BaseScope extends DbScope {
      import database.api._
      val documentSet = factory.documentSet()
    }

    "have sourceDocumentSetId on a clone job" in new BaseScope {
      factory.documentSetCreationJob(
        documentSetId=documentSet.id,
        jobType=Type.Clone,
        state=State.NotStarted,
        sourceDocumentSetId=Some(12345L)
      )
      val cloneJob = PersistentDocumentSetCreationJob.findJobsWithState(DeprecatedState.NotStarted).head
      cloneJob.sourceDocumentSetId must beSome(12345L)
    }

    "find first job with a state, ordered by id" in new BaseScope {
      val documentSet2 = factory.documentSet()
      factory.documentSetCreationJob(id=124L, documentSetId=documentSet.id, state=State.NotStarted)
      factory.documentSetCreationJob(id=123L, documentSetId=documentSet2.id, state=State.NotStarted)

      val firstNotStartedJob = PersistentDocumentSetCreationJob.findFirstJobWithState(DeprecatedState.NotStarted)

      firstNotStartedJob must beSome
      // test documentSetId since we can't get at job.id directly
      firstNotStartedJob.get.documentSetId must beEqualTo(documentSet2.id)
    }

    "with a job" should {
      trait JobSetup extends BaseScope {
        factory.documentSetCreationJob(documentSetId=documentSet.id, state=State.NotStarted)
        val notStartedJob = PersistentDocumentSetCreationJob.findJobsWithState(DeprecatedState.NotStarted).head
      }

      "update job state" in new JobSetup {
        notStartedJob.state = DeprecatedState.InProgress
        notStartedJob.update

        val remainingNotStartedJobs = PersistentDocumentSetCreationJob.findJobsWithState(DeprecatedState.NotStarted)
        remainingNotStartedJobs must beEmpty
      }

      "update percent complete" in new JobSetup {
        notStartedJob.fractionComplete = 0.5
        notStartedJob.update
        val job = PersistentDocumentSetCreationJob.findJobsWithState(DeprecatedState.NotStarted).head

        job.fractionComplete must beEqualTo(0.5)
      }

      "update job status" in new JobSetup {
        val status = "status message"
        notStartedJob.statusDescription = Some(status)
        notStartedJob.update
        val job = PersistentDocumentSetCreationJob.findJobsWithState(DeprecatedState.NotStarted).head

        job.statusDescription must beSome
        job.statusDescription.get must beEqualTo(status)
      }

      "have empty username and password if not available" in new JobSetup {
        notStartedJob.documentCloudUsername must beNone
        notStartedJob.documentCloudPassword must beNone
      }

      "refresh job state" in new JobSetup {
        import database.api._
        blockingDatabase.runUnit {
          DocumentSetCreationJobs
            .filter(_.id === notStartedJob.id)
            .map(_.state).update(State.Cancelled)
        }

        notStartedJob.checkForCancellation
        notStartedJob.state must beEqualTo(DeprecatedState.Cancelled)
      }
    }

    "with a queue of jobs" should {
      trait JobQueueSetup extends BaseScope {
        factory.documentSetCreationJob(documentSetId=documentSet.id, state=State.NotStarted)
        factory.documentSetCreationJob(documentSetId=documentSet.id, state=State.InProgress)
        factory.documentSetCreationJob(documentSetId=documentSet.id, state=State.NotStarted)
        factory.documentSetCreationJob(documentSetId=documentSet.id, state=State.InProgress)
      }

      "find all submitted jobs" in new JobQueueSetup {
        val notStarted = PersistentDocumentSetCreationJob.findJobsWithState(DeprecatedState.NotStarted)

        notStarted must have size (2)
        notStarted.map(_.state).distinct must beEqualTo(Seq(DeprecatedState.NotStarted))
        notStarted.map(_.documentSetId).distinct must beEqualTo(Seq(documentSet.id))
      }

      "find all in progress jobs" in new JobQueueSetup {
        val inProgress = PersistentDocumentSetCreationJob.findJobsWithState(DeprecatedState.InProgress)

        inProgress must have size (2)
        inProgress.map(_.state).distinct must beEqualTo(Seq(DeprecatedState.InProgress))
      }
    }

    "with a DocumentCloud job" should {
      trait DocumentCloudJobSetup extends BaseScope {
        factory.documentSetCreationJob(
          documentSetId=documentSet.id,
          jobType=Type.DocumentCloud,
          state=State.NotStarted,
          documentcloudUsername=Some("user@documentcloud.org"),
          documentcloudPassword=Some("dcPassword")
        )
        val dcJob = PersistentDocumentSetCreationJob.findJobsWithState(DeprecatedState.NotStarted).head
      }

      "have username and password if available" in new DocumentCloudJobSetup {
        dcJob.documentCloudUsername must beSome("user@documentcloud.org")
        dcJob.documentCloudPassword must beSome("dcPassword")
      }

      "have splitDocuments set" in new DocumentCloudJobSetup {
        dcJob.splitDocuments must beFalse
      }
    }

    "with a CSV import job" should {
      trait CsvImportJobSetup extends BaseScope {
        import database.api._

        val contentsOid: Long = blockingDatabase.run(database.largeObjectManager.create.transactionally)

        factory.documentSetCreationJob(
          documentSetId=documentSet.id,
          state=State.NotStarted,
          jobType=Type.CsvUpload,
          contentsOid=Some(contentsOid)
        )
        val csvImportJob = PersistentDocumentSetCreationJob.findJobsWithState(DeprecatedState.NotStarted).head

        override def after = {
          super.after
          try {
            blockingDatabase.runUnit(database.largeObjectManager.unlink(contentsOid).transactionally)
          } catch {
            case e: java.sql.SQLException => // ignore double-unlink
          }
        }
      }

      "delete itself and LargeObject on completion, if not cancelled" in new CsvImportJobSetup {
        csvImportJob.delete

        PersistentDocumentSetCreationJob.findJobsWithState(DeprecatedState.NotStarted) must beEmpty
        blockingDatabase.run(database.largeObjectManager.unlink(contentsOid)) must throwA[Exception]
      }

      "have a type" in new CsvImportJobSetup {
        csvImportJob.jobType must beEqualTo(DeprecatedType.CsvUpload)
      }

      "have contentsOid if available" in new CsvImportJobSetup {
        csvImportJob.contentsOid must beSome
        csvImportJob.contentsOid.get must beEqualTo(contentsOid)
      }
    }

    "with a cancelled job" should {
      trait CancelledJob extends BaseScope {
        var cancelNotificationReceived: Boolean = false

        factory.documentSetCreationJob(documentSetId=documentSet.id, state=State.Cancelled)
        val cancelledJob = PersistentDocumentSetCreationJob.findJobsWithState(DeprecatedState.Cancelled).head
        cancelledJob.observeCancellation(j => cancelNotificationReceived = true)
      }

      "not update job state if cancelled" in new CancelledJob {
        cancelledJob.state = DeprecatedState.InProgress
        cancelledJob.update
        PersistentDocumentSetCreationJob.findJobsWithState(DeprecatedState.InProgress) must be empty
        val numberOfCancelledJobs = PersistentDocumentSetCreationJob.findJobsWithState(DeprecatedState.Cancelled).length
        numberOfCancelledJobs must beEqualTo(1)
      }

      "Notify cancellation observer if job is cancelled" in new CancelledJob {
        cancelledJob.delete

        cancelNotificationReceived must beTrue
        PersistentDocumentSetCreationJob.findJobsWithState(DeprecatedState.Cancelled).headOption must beSome
      }
    }
  }
}
