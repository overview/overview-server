package views.html.Session

import jodd.lagarto.dom.jerry.Jerry.jerry
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.data.Form
import play.api.data.Forms.{email,nonEmptyText,mapping}
import play.api.Play.{start, stop}
import play.api.test.FakeApplication

class new_Spec extends Specification {
  trait OurContext extends Scope {
    lazy val loginForm = controllers.forms.LoginForm()
    lazy val userForm = controllers.forms.UserForm()

    lazy implicit val flash = play.api.mvc.Flash()
    lazy val body = new_(loginForm, userForm).body
    lazy val j = jerry(body)
    def $(selector: String) = j.$(selector)
  }

  step(start(FakeApplication()))

  "new_()" should {
    "show two form" in new OurContext {
      $("form").length.must(beEqualTo(2))
    }

    "show a confirm-password field" in new OurContext {
      $("input[type=password].confirm-password").length.must(beEqualTo(1))
    }
  }

  step(stop)
}
