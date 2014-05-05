package org.overviewproject.jobhandler.filegroup

import akka.actor.Actor

object ProgressReporterProtocol {
  case class StartJob(jobId: Long, numberOfTasks: Int)
  case class StartTask(jobId: Long, taskId: Long)
}

case class JobProgress(numberOfTasks: Int, tasksStarted: Int = 0, fraction: Double = 0.0) {
  def startTask: JobProgress = this.copy(tasksStarted = tasksStarted + 1)
  def completeTask: JobProgress = this.copy(fraction = tasksStarted.toDouble / numberOfTasks)
}

trait ProgressReporter extends Actor {
  import ProgressReporterProtocol._

  private var jobProgress: Map[Long, JobProgress] = Map.empty

  protected val storage: Storage

  protected trait Storage {
    def updateProgress(jobId: Long, fraction: Double, description: String): Unit
  }

  def receive = {
    case StartJob(jobId, numberOfTasks) => {
      val initialProgress = JobProgress(numberOfTasks)
      jobProgress += (jobId -> initialProgress)
      storage.updateProgress(jobId, 0, description(initialProgress))
    }
    case StartTask(jobId, taskId) => {
      jobProgress.get(jobId).map { p =>
        val updatedProgress = p.startTask

        jobProgress += (jobId -> updatedProgress)
        storage.updateProgress(jobId, updatedProgress.fraction, description(updatedProgress))
      }
    }
  }

  private def description(progress: JobProgress): String =
    s"processing_files:${progress.tasksStarted}:${progress.numberOfTasks}"
}