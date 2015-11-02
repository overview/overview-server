package controllers.forms

import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.data.FormError

class MassUploadControllerFormSpec extends Specification {
  "#new_" should {
    trait NewScope extends Scope {
      def parse(data: (String,String)*) = {
        MassUploadControllerForm.new_.bind(Map(data: _*))
      }
    }

    "return all the fields" in new NewScope {
      val form = parse("name" -> "foo", "lang" -> "en", "split_documents" -> "true")
      form.value must beSome("foo", "en", true)
    }

    "fail on unsupported language" in new NewScope {
      val form = parse("name" -> "foo", "lang" -> "bar")
      form.error("lang") must beSome(FormError("lang", "forms.validation.unsupportedLanguage", Seq("bar")))
    }

    "fail if name is missing" in new NewScope {
      val form = parse("lang" -> "en")
      form.error("name") must beSome(FormError("name", "error.required"))
    }

    "fail if name is blank" in new NewScope {
      val form = parse("name" -> "", "lang" -> "en")
      form.error("name") must beSome(FormError("name", "forms.validation.blankText"))
    }

    "set defaults" in new NewScope {
      val form = parse("name" -> "foo", "lang" -> "en")
      form.value must beSome("foo", "en", false)
    }
  }
}
