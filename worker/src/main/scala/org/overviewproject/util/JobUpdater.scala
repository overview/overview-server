package org.overviewproject.util

import scala.concurrent.Future

import org.overviewproject.database.HasDatabase
import org.overviewproject.models.DocumentSetCreationJob
import org.overviewproject.models.DocumentSetCreationJobState

trait JobUpdater extends HasDatabase {
  import databaseApi._

  def updateValidJob(job: DocumentSetCreationJob): Future[Unit] = {
    database.runUnit(sqlu"""
      WITH ids AS (
        SELECT id FROM document_set_creation_job
        WHERE id = ${job.id} AND state <> ${DocumentSetCreationJobState.Cancelled.id}
      )
      UPDATE document_set_creation_job
      SET state = ${job.state.id},
          retry_attempts = ${job.retryAttempts},
          status_description = ${job.statusDescription}
      WHERE id IN (SELECT id FROM ids)    
    """)
  }
}
