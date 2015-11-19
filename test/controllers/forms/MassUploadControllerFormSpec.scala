package controllers.forms

import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.data.FormError
import play.api.libs.json.Json

class MassUploadControllerFormSpec extends Specification {
  "#new_" should {
    trait NewScope extends Scope {
      def parse(data: (String,String)*) = {
        MassUploadControllerForm.new_.bind(Map(data: _*))
      }
    }

    "return all the fields" in new NewScope {
      val form = parse(
        "name" -> "foo",
        "lang" -> "en",
        "split_documents" -> "true",
        "metadata_json" -> "{ \"foo\": \"bar\" }"
      )
      form.value must beSome("foo", "en", true, Json.obj("foo" -> "bar"))
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
      form.value must beSome("foo", "en", false, Json.obj())
    }
  }
}
