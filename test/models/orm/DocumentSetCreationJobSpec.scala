

package models.orm

import anorm._
import helpers.DbTestContext
import java.sql.Connection
import models.orm.DocumentSetCreationJobState._
import org.specs2.mutable.Specification
import play.api.Play.{ start, stop }
import play.api.test.FakeApplication
import testutil.DbSetup._

class DocumentSetCreationJobSpec extends Specification {

  step(start(FakeApplication()))

  "DocumentSetCreationJob" should {

    def createJobs(state: DocumentSetCreationJobState)(implicit c: Connection): Seq[DocumentSetCreationJob] = {
      val documentSets = for (i <- 1 to 5) yield insertDocumentSet("set-" + i)
      val jobs = documentSets.map(DocumentSetCreationJob(_, state = state))
      jobs.map(Schema.documentSetCreationJobs.insert)
    }

    "Return 0 jobsAheadInQueue for non-Submitted jobs" in new DbTestContext {
      val documentSetId = insertDocumentSet("DocumentSetCreationJobQueueSpec")
      val job = DocumentSetCreationJob(documentSetId, state = InProgress)
      Schema.documentSetCreationJobs.insert(job)

      job.jobsAheadInQueue must be equalTo (0l)
    }

    "Return jobsAheadInQueue in queue of NotStarted jobs" in new DbTestContext {
      val notStartedJobs = createJobs(NotStarted)

      val inProgressJobs = createJobs(InProgress)

      val expectedJobsAheadInQueue = (1l to 5l).toIndexedSeq
      notStartedJobs.map(_.jobsAheadInQueue) must be equalTo (expectedJobsAheadInQueue)
    }

  }

  step(stop)

}

