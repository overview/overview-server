package views.helper

import play.api.i18n.Lang

import models.orm.DocumentSetCreationJob

object DocumentSetHelper {
  /**
   * @param jobDescriptionKey A key, like "clustering_level:4"
   * @returns A translated string, like "Clustering (4)"
   */
  def jobDescriptionKeyToMessage(jobDescriptionKey: String)(implicit lang: Lang): String = {
    val keyWithArg = """(.*):(.*)""".r
    val m = views.ScopedMessages("views.DocumentSet._documentSet.job_state_description")

    jobDescriptionKey match {
      case keyWithArg(key, arg) => m(key, arg)
      case "" => ""
      case key => m(key)
    }
  }

  /**
   * @param job A DocumentSetCreationJob
   * @returns A translated string, like "Clustering (4)"
   */
  def jobDescriptionMessage(job: DocumentSetCreationJob)(implicit lang: Lang): String = {
    val n = job.jobsAheadInQueue

    if (n > 0) {
      views.Magic.t("views.DocumentSet._documentSet.jobs_to_process", n)
    } else {
      jobDescriptionKeyToMessage(job.stateDescription)
    }
  }
}
