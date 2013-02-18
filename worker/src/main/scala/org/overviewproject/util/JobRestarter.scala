/*
 * JobRestarter.scala
 *
 * Overview Project
 * Created by Jonas Karlsson, Oct 2012
 */
package org.overviewproject.util

import java.sql.Connection
import persistence.DocumentSetCleaner
import persistence.PersistentDocumentSetCreationJob
import org.overviewproject.tree.orm.DocumentSetCreationJobState._

/**
 * Removes data related to documentsets in jobs, and resets job state to Submitted.
 */
class JobRestarter(cleaner: DocumentSetCleaner) {

  def restart(jobs: Seq[PersistentDocumentSetCreationJob])(implicit c: Connection) {
    jobs.map { j => 
      cleaner.clean(j.documentSetId)
      j.state = NotStarted
      j.update
      
    }
  }
}
