package controllers.forms

import org.overviewproject.test.Specification
import org.specs2.specification.Scope
import play.api.data.Form
import org.specs2.execute.Result

import org.overviewproject.models.DocumentSet
import org.overviewproject.test.factories.PodoFactory

class DocumentSetUpdateFormSpec extends Specification {

  "DocumentSetUpdateForm" should {

    trait UpdateFormContext extends Scope {
      val documentSet = PodoFactory.documentSet(1L, title="title", isPublic=false)
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
        "title" -> "new title"
      )

      checkFormValues(form, data) { d => 
        d.id must beEqualTo(documentSet.id)
        d.public must beFalse
        d.title must be equalTo("new title")
      }
    }

    "accept one parameter" in new UpdateFormContext {
      val data = Map("public" -> "true")

      checkFormValues(form, data) { d => 
        d.id must beEqualTo(documentSet.id)
        d.public must beTrue
        d.title must be equalTo documentSet.title
      }
    }
  }
}
