package controllers.forms

import play.api.data.{ Form, Forms }
import play.api.data.validation.Constraints

import org.overviewproject.tree.orm.{DocumentSetCreationJob,DocumentSetCreationJobState}
import org.overviewproject.tree.DocumentSetCreationJobType

object TreeCreationJobForm {
  private def buildJob(documentSetId: Long)(
    title: String,
    tagId: Option[Long],
    lang: String,
    suppliedStopWords: String,
    importantWords: String
  ) = DocumentSetCreationJob(
    documentSetId = documentSetId,
    jobType = DocumentSetCreationJobType.Recluster,
    treeTitle = Some(title),
    tagId = tagId,
    lang = lang,
    suppliedStopWords = suppliedStopWords,
    importantWords = importantWords,
    state = DocumentSetCreationJobState.NotStarted
  )

  def apply(documentSetId: Long) : Form[DocumentSetCreationJob] = Form(
    Forms.mapping(
      "tree_title" -> Forms.text.transform(_.trim, identity[String]).verifying(Constraints.nonEmpty),
      "tag_id" -> Forms.optional(Forms.longNumber),
      "lang" -> Forms.nonEmptyText.verifying(validation.supportedLang),
      "supplied_stop_words" -> Forms.text,
      "important_words" -> Forms.text
    )
    (buildJob(documentSetId))
    ((job) => Some((job.treeTitle.getOrElse(""), job.tagId, job.lang, job.suppliedStopWords, job.importantWords)))
  )
}
