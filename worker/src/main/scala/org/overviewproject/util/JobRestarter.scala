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

  def restart(jobs: Seq[PersistentDocumentSetCreationJob]): Unit = 
    jobs.map { j => 
      cleaner.clean(j.id, j.documentSetId)
      searchIndex.deleteDocumentSetAliasAndDocuments(j.documentSetId)
      j.state = NotStarted
      j.update
      

  }
}
