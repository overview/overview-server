package controllers.forms

import play.api.data.{ Form, Forms }
import play.api.data.validation.Constraints

import com.overviewdocs.models.Tree

object TreeCreationJobForm {
  private def buildJob(documentSetId: Long)(
    title: String,
    tagId: Option[Long],
    lang: String,
    suppliedStopWords: String,
    importantWords: String
  ) = Tree.CreateAttributes(
    documentSetId = documentSetId,
    lang = lang,
    suppliedStopWords = suppliedStopWords,
    importantWords = importantWords,
    title = title,
    description = "",
    tagId = tagId
  )

  def apply(documentSetId: Long): Form[Tree.CreateAttributes] = Form(
    Forms.mapping(
      "tree_title" -> Forms.text.transform(_.trim, identity[String]).verifying(Constraints.nonEmpty),
      "tag_id" -> Forms.optional(Forms.longNumber),
      "lang" -> Forms.nonEmptyText.verifying(validation.supportedLang),
      "supplied_stop_words" -> Forms.text,
      "important_words" -> Forms.text
    )
    (buildJob(documentSetId))
    ((tree) => Some((tree.title, tree.tagId, tree.lang, tree.suppliedStopWords, tree.importantWords)))
  )
}
