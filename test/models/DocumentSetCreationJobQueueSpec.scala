

package models

import anorm._
import helpers.DbSetup._
import helpers.DbTestContext
import java.sql.Connection
import models.orm.DocumentSetCreationJob.State._   
import org.specs2.mutable.Specification
import play.api.Play.{ start, stop }
import play.api.test.FakeApplication

class DocumentSetCreationJobQueueSpec extends Specification {

  step(start(FakeApplication()))
    
  "DocumentSetCreationJobQueue" should {

    def insertDocumentSetCreationJob(documentSetId: Long, state: Int)(implicit c: Connection): Long = {
      SQL("""
        INSERT INTO document_set_creation_job (document_set_id, state)
        VALUES ({documentSetId}, {state})
        """).on("documentSetId" -> documentSetId, "state" -> state).
        executeInsert().getOrElse(throw new Exception("failed Insert"))
    }

    "Return -1th position for non-Submitted jobs" in new DbTestContext {
      val documentSetId = insertDocumentSet("DocumentSetCreationJobQueueSpec")
      insertDocumentSetCreationJob(documentSetId, NotStarted.id)
      val qPosition = DocumentSetCreationJobQueue.position(0l)

      qPosition.position must be equalTo(-1l)
      qPosition.length must be equalTo(1l)
    }

    "Return position in queue of NotStarted jobs" in new DbTestContext {
      val notStartedSets = for (i <- 1 to 5) yield insertDocumentSet("set-" + i)
      val notStartedJobs = notStartedSets.map(id => insertDocumentSetCreationJob(id, NotStarted.id))

      val inProgressSets = for (i <- 1 to 5) yield insertDocumentSet("in progress-" + i)
      inProgressSets.map(id => insertDocumentSetCreationJob(id, InProgress.id))

      val expectedPositions = (0 to 4).map(DocumentSetCreationJobQueue.Position(_, 5l))
      notStartedJobs.map(DocumentSetCreationJobQueue.position) must be equalTo(expectedPositions)
    }
    
  }

  step(stop)

}










