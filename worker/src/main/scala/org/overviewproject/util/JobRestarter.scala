/*
 * JobRestarter.scala
 *
 * Overview Project
 * Created by Jonas Karlsson, Oct 2012
 */
package org.overviewproject.util

import org.overviewproject.persistence.{ DocumentSetCleaner, PersistentDocumentSetCreationJob }

import org.overviewproject.tree.orm.DocumentSetCreationJobState._

/**
 * Removes data related to documentsets in jobs, and resets job state to Submitted.
 */
class JobRestarter(cleaner: DocumentSetCleaner, searchIndex: SearchIndex) {

  private val MaxRetryAttempts = Configuration.getInt("max_job_retry_attempts")

  def restart(jobs: Seq[PersistentDocumentSetCreationJob]): Unit =
    jobs.map { j =>
      if (j.retryAttempts < MaxRetryAttempts) {
        cleaner.clean(j.id, j.documentSetId)
        searchIndex.deleteDocumentSetAliasAndDocuments(j.documentSetId)
        j.retryAttempts = j.retryAttempts + 1
        j.state = NotStarted
      }
      else {
        j.statusDescription = Some("max_retry_attempts")
        j.state = Error
      }
      
      j.update

    }
}
