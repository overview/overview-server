package views.helper

import play.api.i18n.Lang

import org.overviewproject.tree.orm.DocumentSetCreationJob

object DocumentSetHelper {
  /**
   * @param jobDescriptionKey A key, like "clustering_level:4"
   * @return A translated string, like "Clustering (4)"
   */
  def jobDescriptionKeyToMessage(jobDescriptionKey: String)(implicit lang: Lang): String = {
    val keyAndArgs : Seq[String] = jobDescriptionKey.split(':')
    val key = keyAndArgs.head
    if (key == "") {
      ""
    } else {
      val m = views.ScopedMessages("views.DocumentSetCreationJob._documentSetCreationJob.job_state_description")
      m(keyAndArgs.head, keyAndArgs.drop(1) : _*)
    }
  }

  /**
   * @param job A DocumentSetCreationJob
   * @param nAheadInQueue Number of jobs ahead of this one in the queue
   * @return A translated string, like "Clustering (4)"
   */
  def jobDescriptionMessage(job: DocumentSetCreationJob, nAheadInQueue: Long)(implicit lang: Lang): String = {
    if (nAheadInQueue > 0) {
      views.Magic.t("views.DocumentSetCreationJob._documentSetCreationJob.jobs_to_process", nAheadInQueue)
    } else {
      jobDescriptionKeyToMessage(job.statusDescription)
    }
  }
}
