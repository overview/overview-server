package views.helper

import play.api.i18n.Messages

object DocumentSetHelper {
  /**
   * @param jobDescriptionKey A key, like "clustering_level:4"
   * @return A translated string, like "Clustering (4)"
   */
  def jobDescriptionKeyToMessage(jobDescriptionKey: String)(implicit messages: Messages): String = {
    val keyAndArgs : Seq[String] = jobDescriptionKey.split(':')
    val key = keyAndArgs.head
    if (key == "") {
      ""
    } else {
      val m = views.ScopedMessages("views.ImportJob._documentSetCreationJob.job_state_description", messages)
      m(keyAndArgs.head, keyAndArgs.drop(1) : _*)
    }
  }
}
