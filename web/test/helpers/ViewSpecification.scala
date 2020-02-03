package views

import jodd.lagarto.dom.jerry.Jerry.jerry
import org.specs2.matcher.JsonMatchers
import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import play.api.i18n.{Lang,Messages}
import play.api.libs.json.JsValue
import play.api.mvc.{Flash,RequestHeader}
import play.twirl.api.{Html,HtmlFormat}
import play.api.test.FakeRequest
import scala.collection.immutable

import models.{User=>UserModel}
import com.overviewdocs.models.UserRole
import test.helpers.MockMessagesApi

class ViewSpecification extends test.helpers.InAppSpecification with Mockito with JsonMatchers {
  // Need fake application, because some views show CSRF tokens
  def fakeUser: UserModel = UserModel(email="user@example.org", role=UserRole.NormalUser)

  trait ViewSpecificationScope[Result] extends Scope {
    class MockMain extends views.html.main(
      mock[controllers.AssetsFinder],
      mock[play.api.Configuration],
      mock[play.api.i18n.MessagesApi],
      mock[views.html.components.navbar],
      mock[models.IntercomConfiguration]
    ) {
      override def apply(
        optionalUser: Option[UserModel],
        title: String,
        bodyClass: String,
        h1: String,
        javascripts: Html,
        jsMessageKeys: Seq[String],
        optionalDocumentSet: Option[com.overviewdocs.models.DocumentSet]
      )(
        content: Html
      )(implicit messages: Messages, flash: Flash, request: RequestHeader): Html = {
        HtmlFormat.fill(immutable.Seq(
          HtmlFormat.raw("<main>"),
          content,
          HtmlFormat.raw("</main>")
        ))
      }
    }

    class MockMainWithSidebar extends views.html.layouts.mainWithSidebar(
      mock[views.html.main]
    ) {
      override def apply(
        user: UserModel,
        title: String,
        bodyClass: String,
        javascripts: Html,
        jsMessageKeys: Seq[String]
      )(
        sidebar: Html
      )(
        content: Html
      )(implicit messages: Messages, flash: Flash, request: RequestHeader): Html = {
        HtmlFormat.fill(immutable.Seq(
          HtmlFormat.raw("<main>"),
          content,
          HtmlFormat.raw("</main><aside>"),
          sidebar,
          HtmlFormat.raw("</aside>")
        ))
      }
    }

    val factory = com.overviewdocs.test.factories.PodoFactory
    implicit def request: RequestHeader = FakeRequest()
    implicit def flash: Flash = Flash()
    implicit def messages: Messages = test.helpers.MockMessages.default

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
