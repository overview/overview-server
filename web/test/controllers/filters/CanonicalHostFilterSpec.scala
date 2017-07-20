package controllers.filters

import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.Configuration
import play.api.libs.streams.Accumulator
import play.api.mvc.{AnyContentAsEmpty,EssentialAction,Headers,Results}
import play.api.mvc.request.RequestFactory
import play.api.test.{EssentialActionCaller,FakeRequestFactory,Helpers,Writeables}
import scala.concurrent.Future

import test.helpers.InAppSpecification

class CanonicalHostFilterSpec extends InAppSpecification {
  class BaseScope(val canonicalUrl: String, val uri: String) extends Scope with EssentialActionCaller with Writeables {
    val h = Helpers
    implicit val timeout = new akka.util.Timeout(scala.concurrent.duration.Duration(1000, "hours"))

    val config = Map("overview.canonical_url" -> canonicalUrl)
    lazy val defaultAction = EssentialAction { _ => Accumulator.done(Results.Ok("pong")) }
    lazy val requestFactory = new FakeRequestFactory(RequestFactory.plain)
    val method = "GET"
    val urlHost = uri.split("/")(2)
    val urlPath = if (uri.matches("\\Ahttps?://.*/.*\\Z")) { s"/${uri.split("/", 4)(3)}" } else { "" }
    val headers = Headers("Host" -> urlHost)
    val body = AnyContentAsEmpty
    val isSecure = uri.startsWith("https:")
    lazy val request = requestFactory("GET", urlPath, headers, body, secure=isSecure)
    lazy val filter = new CanonicalHostFilter(Configuration.from(config))
    lazy val result = call(filter(defaultAction), request, AnyContentAsEmpty)
  }

  "CanonicalHostFilter" should {
    "redirect from a request to the canonical URL" in new BaseScope("https://www.overviewdocs.com", "https://www.overviewproject.org") {
      h.status(result) must beEqualTo(h.MOVED_PERMANENTLY)
      h.header("Location", result) must beSome("https://www.overviewdocs.com")
    }

    "not redirect on correct URL" in new BaseScope("https://www.overviewdocs.com", "https://www.overviewdocs.com") {
      h.status(result) must beEqualTo(h.OK)
      h.header("Location", result) must beNone
    }

    "redirect from HTTP to HTTPS on the same host" in new BaseScope("https://www.overviewdocs.com", "http://www.overviewdocs.com") {
      h.header("Location", result) must beSome("https://www.overviewdocs.com")
    }

    "redirect if correct URL but wrong port" in new BaseScope("https://www.overviewdocs.com", "https://www.overviewdocs.com:9000") {
      h.status(result) must beEqualTo(h.MOVED_PERMANENTLY)
      h.header("location", result) must beSome("https://www.overviewdocs.com")
    }

    "include path and query string in redirect" in new BaseScope("https://www.overviewdocs.com", "http://www.overviewdocs.com/documentsets/42?foo=bar&bar=baz") {
      h.status(result) must beEqualTo(h.MOVED_PERMANENTLY)
      h.header("location", result) must beSome("https://www.overviewdocs.com/documentsets/42?foo=bar&bar=baz")
    }

    "redirect POST requests -- it's better than handling them" in new BaseScope("https://www.overviewdocs.com", "http://www.overviewdocs.com") {
      override val method = "POST"
      h.status(result) must beEqualTo(h.MOVED_PERMANENTLY)
    }

    "do nothing when there is no canonical URL" in new BaseScope("", "http://www.overviewdocs.com") {
      h.status(result) must beEqualTo(h.OK)
      h.header("Location", result) must beNone
    }
  }
}
