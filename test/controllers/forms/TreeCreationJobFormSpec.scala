package controllers.forms

import play.api.data.FormError

import com.overviewdocs.models.Tree

class TreeCreationJobFormSpec extends test.helpers.FormSpecification {
  trait JobApplyScope extends ApplyScope[Tree.CreateAttributes] {
    val documentSetId = 4L // random
    override def form = TreeCreationJobForm(documentSetId)
  }

  trait ValidScope extends JobApplyScope {
    override def args = Map(
      "tree_title" -> "title",
      "tag_id" -> "",
      "lang" -> "en",
      "supplied_stop_words" -> "",
      "important_words" -> ""
    )
  }

  "TreeCreationJobForm" should {
    "create a correct Tree" in new ValidScope {
      value.map(_.documentSetId) must beSome(documentSetId)
      value.map(_.title) must beSome("title")
      value.flatMap(_.tagId) must beNone
      value.map(_.suppliedStopWords) must beSome("")
      value.map(_.importantWords) must beSome("")
      value.map(_.lang) must beSome("en")
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

    "disallow if title is missing" in new ValidScope {
      override def args = super.args - "tree_title"
      error("tree_title") must beSome(FormError("tree_title", "error.required", Seq()))
    }

    "disallow if title is empty" in new ValidScope {
      override def args = super.args + ("tree_title" -> "")
      error("tree_title") must beSome(FormError("tree_title", "error.required", Seq()))
    }

    "trim the title" in new ValidScope {
      override def args = super.args + ("tree_title" -> " title ")
      value.map(_.title) must beSome("title")
    }

    "disallow if title is just spaces" in new ValidScope {
      override def args = super.args + ("tree_title" -> "  ")
      error("tree_title") must beSome(FormError("tree_title", "error.required", Seq()))
    }

    "set tagId" in new ValidScope {
      override def args = super.args + ("tag_id" -> "1125899906842624")
      value.flatMap(_.tagId) must beSome(1125899906842624L)
    }

    "not set tagId when it is empty" in new ValidScope {
      override def args = super.args + ("tag_id" -> "")
      value.flatMap(_.tagId) must beNone
    }
  }
}
