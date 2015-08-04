package com.overviewdocs.jobhandler.documentset

import com.overviewdocs.tree.orm.finders.DocumentSetComponentFinder
import com.overviewdocs.database.DeprecatedDatabase
import com.overviewdocs.tree.orm.DocumentSetCreationJobState._
import com.overviewdocs.database.orm.stores.DocumentSetCreationJobStore

trait JobStatusChecker {
  def isJobRunning(documentSetId: Long): Boolean
  def cancelJob(documentSetId: Long): Boolean
}

object JobStatusChecker {
  
  import com.overviewdocs.database.orm.Schema._
  
  def apply(): JobStatusChecker = new JobStatusChecker {
    override def isJobRunning(documentSetId: Long): Boolean = DeprecatedDatabase.inTransaction {
      DocumentSetComponentFinder(documentSetCreationJobs).byDocumentSet(documentSetId).exists { j =>
        j.state ==  InProgress ||
        j.state == Cancelled
      }
    }
    
    override def cancelJob(documentSetId: Long): Boolean = DeprecatedDatabase.inTransaction {
      DocumentSetCreationJobStore.cancelByDocumentSet(documentSetId).nonEmpty
    }
  }
}
