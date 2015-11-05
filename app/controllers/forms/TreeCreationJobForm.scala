package controllers.forms

import play.api.data.{ Form, Forms }
import play.api.data.validation.Constraints

import com.overviewdocs.models.{DocumentSetCreationJob,DocumentSetCreationJobState,DocumentSetCreationJobType}

object TreeCreationJobForm {
  private def buildJob(documentSetId: Long)(
    title: String,
    tagId: Option[Long],
    lang: String,
    suppliedStopWords: String,
    importantWords: String
  ) = DocumentSetCreationJob.CreateAttributes(
    documentSetId = documentSetId,
    jobType = DocumentSetCreationJobType.Recluster,
    retryAttempts = 0,
    lang = lang,
    suppliedStopWords = suppliedStopWords,
    importantWords = importantWords,
    splitDocuments = false,
    documentcloudUsername = None,
    documentcloudPassword = None,
    contentsOid = None,
    sourceDocumentSetId = None,
    treeTitle = Some(title),
    treeDescription = None,
    tagId = tagId,
    state = DocumentSetCreationJobState.NotStarted,
    fractionComplete = 0,
    statusDescription = "",
    canBeCancelled = true
  )

  def apply(documentSetId: Long) : Form[DocumentSetCreationJob.CreateAttributes] = Form(
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
