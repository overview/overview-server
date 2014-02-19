package controllers.forms

import play.api.data.{ Form, Forms }
import play.api.data.validation.Constraints

import org.overviewproject.tree.orm.{DocumentSetCreationJob,DocumentSetCreationJobState}
import org.overviewproject.tree.DocumentSetCreationJobType

object TreeCreationJobForm {
  private def buildJob(documentSetId: Long)(
    title: String,
    lang: String,
    suppliedStopWords: String,
    importantWords: String
  ) = DocumentSetCreationJob(
    documentSetId = documentSetId,
    jobType = DocumentSetCreationJobType.Recluster,
    treeTitle = Some(title),
    lang = lang,
    suppliedStopWords = suppliedStopWords,
    importantWords = importantWords,
    state = DocumentSetCreationJobState.NotStarted
  )

  def apply(documentSetId: Long) : Form[DocumentSetCreationJob] = Form(
    Forms.mapping(
      "tree_title" -> Forms.text.transform(_.trim, identity[String]).verifying(Constraints.nonEmpty),
      "lang" -> Forms.nonEmptyText.verifying(validation.supportedLang),
      "supplied_stop_words" -> Forms.text,
      "important_words" -> Forms.text
    )
    (buildJob(documentSetId))
    ((job) => Some((job.treeTitle.getOrElse(""), job.lang, job.suppliedStopWords, job.importantWords)))
  )
}
