/*
 * JobRestarter.scala
 *
 * Overview Project
 * Created by Jonas Karlsson, Oct 2012
 */
package org.overviewproject.util

import org.overviewproject.persistence.{ DocumentSetCleaner => OldClean, PersistentDocumentSetCreationJob }
import org.overviewproject.models.DocumentSetCreationJob
import org.overviewproject.models.DocumentSetCreationJobState._
import org.overviewproject.models.tables.DocumentSetCreationJobMappings

trait NewJobRestarter {
  val MaxRetryAttempts = Configuration.getInt("max_job_retry_attempts")
  
  protected val job: DocumentSetCreationJob
  protected val storage: Storage
  
  def restart: Unit = 
    if (job.retryAttempts < MaxRetryAttempts ) attemptRestart
    else failJob
  
    
  protected def attemptRestart: Unit = {
      removeInterruptedJobData
      storage.updateValidJob(job.copy(state = NotStarted, retryAttempts = job.retryAttempts + 1))
    }
  
  protected def failJob: Unit = 
    storage.updateValidJob(job.copy(state = Error, statusDescription = "max_retry_attempts"))
    
  protected def removeInterruptedJobData: Unit 
  
  protected trait Storage {
    def updateValidJob(job: DocumentSetCreationJob)
  }
}

object JobRestarter extends DocumentSetCreationJobMappings {
  import scala.concurrent.ExecutionContext.Implicits.global
  import org.overviewproject.database.Database
  import org.overviewproject.database.Slick.simple._
  import org.overviewproject.models.tables.DocumentSetCreationJobsImpl
  import org.overviewproject.models.DocumentSetCreationJobType._
  import org.overviewproject.models.DocumentSetCreationJobState._
  import org.overviewproject.models.tables.DocumentSetCreationJobs
  
  
  def restartInterruptedJobs: Unit = {
    interruptedJobs.flatMap(createRestarter).map(_.restart)
  } 

  private def interruptedJobs: Seq[DocumentSetCreationJob] = Database.withSlickSession { implicit session =>
    DocumentSetCreationJobs.filter(_.state === InProgress).list
  }
  
  private def createRestarter(job: DocumentSetCreationJob): Option[NewJobRestarter] = job.jobType match {
    case Recluster => Some(ClusteringJobRestarter(job))
    case DocumentCloud => Some(DocumentSetCreationJobRestarter(job, SearchIndex))
    case CsvUpload => Some(DocumentSetCreationJobRestarter(job, SearchIndex))
    case _ => None
  }

  
}
/**
 * Removes data related to documentsets in jobs, and resets job state to Submitted.
 */
class JobRestarter(cleaner: OldClean, searchIndex: SearchIndex) {

  private val MaxRetryAttempts = Configuration.getInt("max_job_retry_attempts")

  def restart(jobs: Seq[PersistentDocumentSetCreationJob]): Unit =
    jobs.map { j =>
      if (j.retryAttempts < MaxRetryAttempts) {
        cleaner.clean(j.id, j.documentSetId)
        searchIndex.deleteDocumentSetAliasAndDocuments(j.documentSetId)
        j.retryAttempts = j.retryAttempts + 1
        j.state = org.overviewproject.tree.orm.DocumentSetCreationJobState.NotStarted
      }
      else {
        j.statusDescription = Some("max_retry_attempts")
        j.state = org.overviewproject.tree.orm.DocumentSetCreationJobState.Error
      }
      
      j.update

    }
}
