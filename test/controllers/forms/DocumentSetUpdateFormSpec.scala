package controllers.forms

import org.overviewproject.test.Specification
import org.specs2.specification.Scope
import models.orm.DocumentSet
import play.api.data.Form
import org.specs2.execute.Result

class DocumentSetUpdateFormSpec extends Specification {

  "DocumentSetUpdateForm" should {

    trait UpdateFormContext extends Scope {
      val documentSet = DocumentSet(1l, title = "title", isPublic = false)
      val form = DocumentSetUpdateForm(documentSet)
    }

    def checkFormValues(form: Form[DocumentSet], data: Map[String, String])(check: DocumentSet => Result): Result =  {
      form.bind(data).fold(
        f => failure("Couldn't bind form"),
        check
      )
    }

    "accept all parameters" in new UpdateFormContext {
      val data = Map(
        "public" -> "false",
      "title" -> "new title")

      checkFormValues(form, data) { d => 
        d.id must beEqualTo(documentSet.id)
        d.isPublic must beFalse
        d.title must be equalTo("new title")
      }
    }

    "accept one parameter" in new UpdateFormContext {
      val data = Map("public" -> "true")

      checkFormValues(form, data) { d => 
        d.id must beEqualTo(documentSet.id)
        d.isPublic must beTrue
        d.title must be equalTo documentSet.title
      }
    }
  }
}
