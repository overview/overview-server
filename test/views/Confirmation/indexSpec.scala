package views.html.Confirmation

import jodd.lagarto.dom.jerry.Jerry.jerry
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.data.Form
import play.api.data.Forms.{mapping,text}
import models.{ConfirmationRequest, OverviewUser}

class indexSpec extends Specification {
  trait OurContext extends Scope {
    lazy implicit val flash = play.api.mvc.Flash()

    lazy val user : Option[models.orm.User] = None
    lazy val emptyForm = Form(mapping(
      "token" -> play.api.data.Forms.text
      )(OverviewUser.findByConfirmationToken(_).getOrElse(throw new Exception("user already confirmed")))(u => Some(u.confirmationToken))
    )
    lazy val form = emptyForm

    lazy val body = index(form).body
    lazy val j = jerry(body)
    def $(selector: String) = j.$(selector)
  }

  "index()" should {
    "show a form" in new OurContext {
      $("form").length.must(beEqualTo(1))
    }
  }
}

