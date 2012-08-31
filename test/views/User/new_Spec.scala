package views.html.User

import jodd.lagarto.dom.jerry.Jerry.jerry
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.data.Form
import play.api.data.Forms.{email,nonEmptyText,mapping}

class new_Spec extends Specification {
  trait OurContext extends Scope {
    lazy val emptyForm = Form(mapping(
      "email" -> email,
      "password" -> nonEmptyText
    )(models.PotentialUser)(u => Some((u.email, u.password))))
    
    lazy val form = emptyForm

    lazy implicit val flash = play.api.mvc.Flash()
    lazy val body = new_(form).body
    lazy val j = jerry(body)
    def $(selector: String) = j.$(selector)
  }

  "new_()" should {
    "show a form" in new OurContext {
      $("form").length.must(beEqualTo(1))
    }

    "show a confirm-password field" in new OurContext {
      $("input[type=password].confirm-password").length.must(beEqualTo(1))
    }
  }
}
