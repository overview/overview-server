package org.overviewproject.jobhandler.documentset

import org.overviewproject.tree.orm.finders.DocumentSetComponentFinder
import org.overviewproject.database.Database
import org.overviewproject.tree.orm.DocumentSetCreationJobState._
import org.overviewproject.database.orm.stores.DocumentSetCreationJobStore

trait JobStatusChecker {
  def isJobRunning(documentSetId: Long): Boolean
  def cancelJob(documentSetId: Long): Boolean
}

object JobStatusChecker {
  
  import org.overviewproject.database.orm.Schema._
  
  def apply(): JobStatusChecker = new JobStatusChecker {
    override def isJobRunning(documentSetId: Long): Boolean = Database.inTransaction {
      DocumentSetComponentFinder(documentSetCreationJobs).byDocumentSet(documentSetId).exists { j =>
        j.state ==  InProgress ||
        j.state == Cancelled
      }
    }
    
    override def cancelJob(documentSetId: Long): Boolean = Database.inTransaction {
      DocumentSetCreationJobStore.cancelByDocumentSet(documentSetId).nonEmpty
    }
  }
}