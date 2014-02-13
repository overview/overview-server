package org.overviewproject.jobhandler.documentset

import org.overviewproject.tree.orm.finders.DocumentSetComponentFinder
import org.overviewproject.database.Database
import org.overviewproject.tree.orm.DocumentSetCreationJob

trait JobStatusChecker {
  def isJobRunning(documentSetId: Long): Boolean
  def runningJob(documentSetId: Long): Option[DocumentSetCreationJob]
}

object JobStatusChecker {

  import org.overviewproject.database.orm.Schema._

  def apply(): JobStatusChecker = new JobStatusChecker {
    override def isJobRunning(documentSetId: Long): Boolean = Database.inTransaction {
      DocumentSetComponentFinder(documentSetCreationJobs).byDocumentSet(documentSetId).headOption.isDefined
    }

    override def runningJob(documentSetId: Long): Option[DocumentSetCreationJob] = Database.inTransaction {
      DocumentSetComponentFinder(documentSetCreationJobs).byDocumentSet(documentSetId).headOption
    }
  }

}