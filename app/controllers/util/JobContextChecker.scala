package controllers.util

import org.overviewproject.tree.orm.DocumentSetCreationJob
import org.overviewproject.tree.DocumentSetCreationJobType._
import org.overviewproject.tree.orm.DocumentSetCreationJobState._

trait JobContextChecker {
  private def jobTest(test: DocumentSetCreationJob => Boolean)(implicit job: Option[DocumentSetCreationJob]): Boolean =
    job.map(test)
      .getOrElse(false)

  def noJobCancelled(implicit job: Option[DocumentSetCreationJob]): Boolean = job.isEmpty

  def notStartedTreeJob(implicit job: Option[DocumentSetCreationJob]): Boolean =
    jobTest { j => (j.jobType == Recluster && j.state == NotStarted) }

  def validTextExtractionJob(implicit job: Option[DocumentSetCreationJob]): Boolean =
    jobTest { j => j.fileGroupId.isDefined }

  def runningTreeJob(implicit job: Option[DocumentSetCreationJob]): Boolean =
    jobTest { j => j.jobType == Recluster && j.state != NotStarted }

  def runningInWorker(implicit job: Option[DocumentSetCreationJob]): Boolean =
    jobTest { j => j.jobType != Recluster && j.state == InProgress }

  def notRunning(implicit job: Option[DocumentSetCreationJob]): Boolean =
    jobTest { j => j.state == NotStarted || j.state == Error || j.state == Cancelled }

  def runningInTextExtractionWorker(implicit job: Option[DocumentSetCreationJob]): Boolean =
    jobTest { j => j.state == FilesUploaded || j.state == TextExtractionInProgress }

}