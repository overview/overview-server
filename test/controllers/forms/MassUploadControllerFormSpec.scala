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

      val name = "file group name"
      val lang = "sv"
      val stopWords = "these words will be ignored"

      val data: Map[String, String]
    }

    trait FormScope extends Scope with FormData {
      def filledForm: Form[(String, String, Option[String])] = {
        val form = MassUploadControllerForm()
        form.bind(data)
      }
    }

    trait ValidForm extends FormData {
      override val data: Map[String, String] = Map(
        Name -> name,
        Lang -> lang,
        SuppliedStopWords -> stopWords)
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

    trait NoSuppliedStopWords extends FormData {
      override val data: Map[String, String] = Map(
        Name -> name,
        Lang -> lang)
    }

    "return all the feels" in new FormScope with ValidForm {
      filledForm.value must beSome(name, lang, Some(stopWords))
    }

    "fail on unsupported language" in new FormScope with InvalidLanguage {
      filledForm.error(Lang) must beSome
    }

    "fail if name is missing" in new FormScope with MissingName {
      filledForm.error(Name) must beSome
    }
    
    "allow missing stop words" in new FormScope with NoSuppliedStopWords {
      filledForm.value must beSome(name, lang, None)
    }
  }
}