package views.html

import jodd.lagarto.dom.jerry.Jerry.jerry
import org.specs2.matcher.JsonMatchers
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.{Fragments, Scope, Step}
import play.api.Play.{start,stop}
import play.api.mvc.Flash
import play.api.templates.Html
import play.api.test.{FakeApplication, FakeHeaders, FakeRequest}

import models.OverviewUser

class ViewSpecification extends Specification with Mockito with JsonMatchers {
  // Need fake application, because some views show CSRF tokens
  override def map(fs: => Fragments) = {
    Step(start(FakeApplication())) ^ super.map(fs) ^ Step(stop)
  }

  def fakeUser : OverviewUser = {
    val ret = mock[OverviewUser]
    ret.email returns "user@example.org"
    ret.isAdministrator returns false
    ret
  }

  trait ViewSpecificationScope[Result] extends Scope {
    implicit def request = FakeRequest()
    implicit def flash = Flash()
    def result: Result
  }

  trait HtmlViewSpecificationScope extends ViewSpecificationScope[Html] {
    lazy val html : Html = result
    private lazy val j = jerry(html.body)
    def $(selector: String) = j.$(selector)
  }
}
