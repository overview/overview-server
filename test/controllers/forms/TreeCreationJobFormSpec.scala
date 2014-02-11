package controllers.forms

import play.api.data.FormError

import org.overviewproject.tree.orm.{DocumentSetCreationJob,DocumentSetCreationJobState}
import org.overviewproject.tree.DocumentSetCreationJobType

class TreeCreationJobFormSpec extends test.helpers.FormSpecification {
  trait JobApplyScope extends ApplyScope[DocumentSetCreationJob] {
    val documentSetId = 4L // random
    override def form = TreeCreationJobForm(documentSetId)
  }

  trait ValidScope extends JobApplyScope {
    val validTitle = "title"
    val requiredQuery = ""
    val validLang = "en"
    val requiredUsername = None
    val requiredPassword = None
    val requiredSplitDocuments = None
    val validSuppliedStopWords = ""
    val validImportantWords = ""

    override def args = Map(
      "title" -> validTitle,
      "lang" -> validLang,
      "supplied_stop_words" -> validSuppliedStopWords,
      "important_words" -> validImportantWords
    )

    def expectedValue = DocumentSetCreationJob(
      documentSetId = documentSetId,
      jobType = DocumentSetCreationJobType.Recluster,
      lang = validLang,
      suppliedStopWords = validSuppliedStopWords,
      importantWords = validImportantWords,
      state = DocumentSetCreationJobState.NotStarted
    )
  }

  "TreeCreationJobForm" should {
    "create a correct DocumentSetCreationJob" in new ValidScope {
      value must beSome(expectedValue)
    }

    "disallow if lang is missing" in new ValidScope {
      override def args = super.args - "lang"
      error("lang") must beSome(FormError("lang", "error.required", Seq()))
    }

    "disallow if lang is empty" in new ValidScope {
      override def args = super.args + ("lang" -> "")
      error("lang") must beSome(FormError("lang", "error.required", Seq()))
    }

    "disallow if lang is not a valid lang" in new ValidScope {
      override def args = super.args + ("lang" -> "invalid language")
      error("lang") must beSome(FormError("lang", "forms.validation.unsupportedLanguage", Seq("invalid language")))
    }

    "disallow if supplied_stop_words is missing" in new ValidScope {
      override def args = super.args - "supplied_stop_words"
      error("supplied_stop_words") must beSome(FormError("supplied_stop_words", "error.required", Seq()))
    }

    "disallow if important_words is missing" in new ValidScope {
      override def args = super.args - "important_words"
      error("important_words") must beSome(FormError("important_words", "error.required", Seq()))
    }
  }
}
