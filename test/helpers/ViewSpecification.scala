package views

import jodd.lagarto.dom.jerry.Jerry.jerry
import org.specs2.matcher.JsonMatchers
import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import play.api.i18n.{Lang,Messages}
import play.api.libs.json.JsValue
import play.api.mvc.{Flash,RequestHeader}
import play.twirl.api.Html
import play.api.test.FakeRequest

import models.{User=>UserModel}
import org.overviewproject.models.UserRole
import test.helpers.MockMessagesApi

class ViewSpecification extends test.helpers.InAppSpecification with Mockito with JsonMatchers {
  // Need fake application, because some views show CSRF tokens
  def fakeUser: UserModel = UserModel(email="user@example.org", role=UserRole.NormalUser)

  trait ViewSpecificationScope[Result] extends Scope {
    val factory = org.overviewproject.test.factories.PodoFactory
    implicit def request: RequestHeader = FakeRequest()
    implicit def flash: Flash = Flash()
    implicit def messages: Messages = new Messages(Lang("en"), new MockMessagesApi())
    def result: Result
  }

  trait HtmlViewSpecificationScope extends ViewSpecificationScope[Html] {
    lazy val html : Html = result
    private lazy val j = jerry(html.body)
    def $(selector: String) = j.$(selector)
  }

  trait JsonViewSpecificationScope extends ViewSpecificationScope[JsValue] {
    lazy val json: String = result.toString
  }
}
