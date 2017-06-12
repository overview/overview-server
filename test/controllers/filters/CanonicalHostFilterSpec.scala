package controllers.filters

import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.Configuration
import play.api.libs.streams.Accumulator
import play.api.mvc.{AnyContentAsEmpty,EssentialAction,Headers,Results}
import play.api.test.{EssentialActionCaller,FakeRequest,Helpers,Writeables}
import scala.concurrent.Future

import test.helpers.InAppSpecification

class CanonicalHostFilterSpec extends InAppSpecification {
  class BaseScope(val canonicalUrl: Option[String], val uri: String) extends Scope with EssentialActionCaller with Writeables {
    val h = Helpers
    implicit val timeout = new akka.util.Timeout(scala.concurrent.duration.Duration(1000, "hours"))

    val config: Map[String, Any] = canonicalUrl match {
      case Some(url) => Map("overview.canonical_url" -> canonicalUrl.get)
      case None => Map()
    }
    lazy val defaultAction = EssentialAction { _ => Accumulator.done(Results.Ok("pong")) }
    lazy val request = FakeRequest().copy(
      secure=uri.startsWith("https:"),
      uri=if (uri.matches("\\Ahttps?://.*/.*\\Z")) { s"/${uri.split("/", 4)(3)}" } else { "" },
      headers=Headers("Host" -> uri.split("/")(2))
    )
    lazy val filter = new CanonicalHostFilter(Configuration.from(config))
    lazy val result = call(filter(defaultAction), request, AnyContentAsEmpty)
  }

  "CanonicalHostFilter" should {
    "redirect from a request to the canonical URL" in new BaseScope(Some("https://www.overviewdocs.com"), "https://www.overviewproject.org") {
      h.status(result) must beEqualTo(h.MOVED_PERMANENTLY)
      h.header("Location", result) must beSome("https://www.overviewdocs.com")
    }

    "not redirect on correct URL" in new BaseScope(Some("https://www.overviewdocs.com"), "https://www.overviewdocs.com") {
      h.status(result) must beEqualTo(h.OK)
      h.header("Location", result) must beNone
    }

    "redirect from HTTP to HTTPS on the same host" in new BaseScope(Some("https://www.overviewdocs.com"), "http://www.overviewdocs.com") {
      h.header("Location", result) must beSome("https://www.overviewdocs.com")
    }

    "redirect if correct URL but wrong port" in new BaseScope(Some("https://www.overviewdocs.com"), "https://www.overviewdocs.com:9000") {
      h.status(result) must beEqualTo(h.MOVED_PERMANENTLY)
      h.header("location", result) must beSome("https://www.overviewdocs.com")
    }

    "include path and query string in redirect" in new BaseScope(Some("https://www.overviewdocs.com"), "http://www.overviewdocs.com/documentsets/42?foo=bar&bar=baz") {
      h.status(result) must beEqualTo(h.MOVED_PERMANENTLY)
      h.header("location", result) must beSome("https://www.overviewdocs.com/documentsets/42?foo=bar&bar=baz")
    }

    "redirect POST requests -- it's better than handling them" in new BaseScope(Some("https://www.overviewdocs.com"), "http://www.overviewdocs.com") {
      override lazy val request = FakeRequest().copy(uri=uri, method="POST")
      h.status(result) must beEqualTo(h.MOVED_PERMANENTLY)
    }

    "do nothing when there is no canonical URL" in new BaseScope(None, "http://www.overviewdocs.com") {
      h.status(result) must beEqualTo(h.OK)
      h.header("Location", result) must beNone
    }
  }
}
