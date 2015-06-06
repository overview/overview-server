package org.overviewproject.jobhandler.filegroup

import scala.annotation.tailrec

import akka.actor.Actor
import akka.actor.Props

import org.overviewproject.database.DeprecatedDatabase
import org.overviewproject.database.orm.finders.DocumentSetCreationJobFinder
import org.overviewproject.database.orm.stores.DocumentSetCreationJobStore
import org.overviewproject.tree.orm.DocumentSetCreationJobState._

/** Description keys for job status */
object JobDescription extends Enumeration {
  type JobDescription = Value
  
  val ProcessUpload = Value(0, "processing_upload")
  val ExtractText = Value(1, "processing_files")
  val CreateDocument = Value(2, "retrieving_documents")
}

import JobDescription._ 

/** 
 *  Messages that can be sent the [[ProgressReporter]] [[Actor]].
 *  The `jobid` used to identify the job for which progress is reported is currently
 *  the [[DocumentSet]] id that the job is processing.
 *  Progress is reported for jobs, job steps, and tasks, by sending Start and Complete messages
 *  to the [[ProgressReporter]]. A Complete message without a corresponding Start message will lead
 *  to undefined results.
 *  A Job can consist of JobSteps or Tasks. A JobStep can consist of other JobSteps or tasks.
 *  For a given Job or JobStep, only one JobStep can be started at a time, but multiple tasks can be started.
 *  
 */
object ProgressReporterProtocol {
  
  /** 
   *  Notify the [[ProgressReporter]] that a Job is starting. This must be the first
   *  message received for any given job.
   *  @param jobId must correspond to the id of a document set being processed by the job.
   *  @param numberOfTasks The number of tasks or steps in the job
   *  @param description The description key used to report status to the user
   */
  case class StartJob(jobId: Long, numberOfTasks: Int, description: JobDescription)
  
  /** 
   *  Notify the [[ProgressReporter]] that the job is completed.
   *  There should be no further progress reported.
   */
  case class CompleteJob(jobId: Long)
  
  

  /**
   * Start a JobStep. JobSteps allow progress to be reported at a more granular level than tasks. Subsequent task 
   * and job step progress reports are assumed to refer to this job step, until [[CompleteJobStep]] is received.
   * A JobStep has a parent Job or JobStep. Completing a JobStep, completes a specific fraction of the overall
   * parent progress. If a Job or JobStep consists of multiple JobSteps, the total fraction specified must be
   * equal to 1.0.
   * If [[StartJobStep]] is received after a [[CompleteJobStep]], for a given `jobId`, the new JobStep is assumed
   * to be next in a sequence of steps for the parent Job or JobStep. 
   * If a [[CompleteJobStep]] has not been received, [[StartJobStep]] will start a child JobStep in the current Job
   * or JobStep.
   * @param numberOfTasksInStep The number of tasks or steps in the job
   * @param description The description key used to report status to the user
   * @param progressFraction The portion of the total progress completed by this JobStep
   */
  case class StartJobStep(jobId: Long, numberOfTasksInStep: Int, progressFraction: Double, description: JobDescription)

  /**
   * Report the current job step as completed.
   */
  case class CompleteJobStep(jobId: Long)

  /** 
   * Report the start of a task.
   */
  case class StartTask(jobId: Long)

  /** Report the completion of a task */  
  case class CompleteTask(jobId: Long)
}

/** 
 *  Actor responsible for updating [[DocumentSetCreationJob]] progress status. Only one [[ProgressReporter]]
 *  should be necessary. Multiple instances trying to report progress on the same job will conflict.
 */
trait ProgressReporter extends Actor {
  import ProgressReporterProtocol._

  private var jobProgress: Map[Long, JobProgress] = Map.empty

  protected val storage: Storage

  protected trait Storage {
    def updateProgress(jobId: Long, fraction: Double, description: String): Unit
    def updateJobState(jobId: Long, state: DocumentSetCreationJobState): Unit
  }

  def receive = {
    case StartJob(jobId, numberOfTasks, description) => updateProgress(jobId, JobProgress(numberOfTasks, description))
    case CompleteJob(jobId) => completeJob(jobId)

    case StartJobStep(jobId, numberOfTasksInStep, progressFraction, description) => {
      startJobStep(jobId, description, numberOfTasksInStep, progressFraction)
    } 
    case CompleteJobStep(jobId) => updateTaskForJob(jobId, _.completeJobStep)

    case StartTask(jobId) => updateTaskForJob(jobId, _.startTask)
    case CompleteTask(jobId) => updateTaskForJob(jobId, _.completeTask)
  }

  private def description(progress: JobProgress): String =
    s"processing_files:${progress.stepInProgress.tasksStarted}:${progress.stepInProgress.numberOfTasks}"

  private def completeJob(jobId: Long): Unit = jobProgress -= jobId

  private def updateTaskForJob(jobId: Long, updateFunction: JobProgress => JobProgress): Unit =
    jobProgress.get(jobId).map { p => updateProgress(jobId, updateFunction(p)) }

  private def updateProgress(jobId: Long, progress: JobProgress): Unit = {
    jobProgress += (jobId -> progress)
    storage.updateProgress(jobId, progress.fraction, progress.stepInProgress.descriptionKey)
  }

  private def startJobStep(jobId: Long, description: JobDescription, numberOfTasksInStep: Int, progressFraction: Double): Unit =
    jobProgress.get(jobId).map { p =>
      jobProgress += (jobId -> p.startJobStep(numberOfTasksInStep, description, progressFraction)) }

  private def completeJobStep(jobId: Long): Unit =
    jobProgress.get(jobId).map { p => jobProgress += (jobId -> p.completeJobStep) }
}

class ProgressReporterImpl extends ProgressReporter {
  class DatabaseStorage extends Storage {
    override def updateProgress(jobId: Long, fraction: Double, description: String): Unit = DeprecatedDatabase.inTransaction {
      DocumentSetCreationJobFinder.byDocumentSetAndStateForUpdate(jobId, TextExtractionInProgress).headOption.map { job =>
        val update = job.copy(fractionComplete = fraction, statusDescription = description)
        DocumentSetCreationJobStore.insertOrUpdate(update)
      }
    }

    override def updateJobState(jobId: Long, state: DocumentSetCreationJobState): Unit = DeprecatedDatabase.inTransaction {
      DocumentSetCreationJobFinder.byDocumentSetAndStateForUpdate(jobId, TextExtractionInProgress).headOption.map { job =>
        val update = job.copy(state = state)
        DocumentSetCreationJobStore.insertOrUpdate(update)
      }
    }
  }

  override protected val storage = new DatabaseStorage
}

object ProgressReporter {
  def apply(): Props = Props[ProgressReporterImpl]
}


/** 
 *  Keeps track of the current progress
 */

case class JobProgress(numberOfTasks: Int, description: JobDescription, tasksStarted: Int = 0, completedStepsFraction: Double = 0.0,
                       currentStepFraction: Double = 1.00, currentStep: Option[JobProgress] = None) {

  def startJobStep(numberOfTasksInStep: Int, jobDescription: JobDescription, progressFractionInStep: Double): JobProgress =
    updateCurrentStep(copy(currentStep = Some(JobProgress(numberOfTasksInStep, description = jobDescription)),
      tasksStarted = tasksStarted + 1,
      currentStepFraction = progressFractionInStep))(_.startJobStep(numberOfTasksInStep, jobDescription, progressFractionInStep))

  def completeJobStep: JobProgress = currentStep.fold(this)(
    _.updateCurrentStep(copy(currentStep = None,
      completedStepsFraction = completedStepsFraction + currentStepFraction))(_.completeJobStep))

  def startTask: JobProgress = updateCurrentStep(copy(tasksStarted = tasksStarted + 1))(_.startTask)

  def completeTask: JobProgress =
    updateCurrentStep(copy(completedStepsFraction = tasksStarted.toDouble / numberOfTasks.toDouble))(_.completeTask)

  def stepInProgress: JobProgress = currentStep.fold(this)(_.stepInProgress)

  def fraction: Double = currentStep.fold(completedStepsFraction)(completedStepsFraction + currentStepFraction * _.fraction)
  
  def descriptionKey: String = s"$description:${tasksStarted}:${numberOfTasks}" 
  
  private def updateCurrentStep(updateJobStep: => JobProgress)(f: JobProgress => JobProgress): JobProgress =
    currentStep.fold(updateJobStep)(p => copy(currentStep = Some(f(p))))

}

