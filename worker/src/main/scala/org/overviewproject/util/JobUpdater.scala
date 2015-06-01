package org.overviewproject.util

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import slick.jdbc.StaticQuery.interpolation

import org.overviewproject.models.DocumentSetCreationJob
import org.overviewproject.database.SlickClient
import org.overviewproject.database.Slick.simple._
import org.overviewproject.models.DocumentSetCreationJobState

trait JobUpdater extends SlickClient {

  def updateValidJob(job: DocumentSetCreationJob): Future[Unit] = db { implicit session =>
    val updatedJob = sqlu"""
          WITH ids AS (
            SELECT id FROM document_set_creation_job
            WHERE id = ${job.id} AND state <> ${DocumentSetCreationJobState.Cancelled.id}
          )
          UPDATE document_set_creation_job
          SET state = ${job.state.id},
              retry_attempts = ${job.retryAttempts},
              status_description = ${job.statusDescription}
          WHERE id IN (SELECT id FROM ids)    
        """
    updatedJob.execute
  }
}