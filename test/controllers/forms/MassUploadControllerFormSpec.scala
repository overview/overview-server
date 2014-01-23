package controllers.forms

import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.data.Form
import play.api.data.Forms._

class MassUploadControllerFormSpec extends Specification {

  "MassUploadControllerForm" should {

    trait FormData {
      val Name = "name"
      val Lang = "lang"
      val SuppliedStopWords = "supplied_stop_words"
      val ImportantWords = "important_words"

      val name = "file group name"
      val lang = "sv"
      val stopWords = "these words will be ignored"
      val importantWords = "important word regex(es)?"

      val data: Map[String, String]
    }

    trait FormScope extends Scope with FormData {
      def filledForm: Form[(String, String, Option[String], Option[String])] = {
        val form = MassUploadControllerForm()
        form.bind(data)
      }
    }

    trait ValidForm extends FormData {
      override val data: Map[String, String] = Map(
        Name -> name,
        Lang -> lang,
        SuppliedStopWords -> stopWords,
        ImportantWords -> importantWords)
    }

    trait InvalidLanguage extends FormData {
      override val data: Map[String, String] = Map(
        Name -> name,
        Lang -> "Unsupported Language",
        SuppliedStopWords -> stopWords)
    }

    trait MissingName extends FormData {
      override val data: Map[String, String] = Map(
        Lang -> "Unsupported Language",
        SuppliedStopWords -> stopWords)
    }

    trait BlankName extends FormData {
      override val data: Map[String, String] = Map(
        Name -> "    ",
        Lang -> lang,
        SuppliedStopWords -> stopWords)
    }
    trait MissingOptionalValues extends FormData {
      override val data: Map[String, String] = Map(
        Name -> name,
        Lang -> lang)
    }


    "return all the feels" in new FormScope with ValidForm {
      filledForm.value must beSome(name, lang, Some(stopWords), Some(importantWords))
    }

    "fail on unsupported language" in new FormScope with InvalidLanguage {
      filledForm.error(Lang) must beSome
    }

    "fail if name is missing" in new FormScope with MissingName {
      filledForm.error(Name) must beSome
    }
    
    "fail if name is blank" in new FormScope with BlankName {
      filledForm.error(Name) must beSome
    }
    
    "allow missing optional values" in new FormScope with MissingOptionalValues {
      filledForm.value must beSome(name, lang, None, None)
    }
  }
}