package controllers.auth

import org.specs2.matcher.JsonMatchers
import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import play.api.mvc.{RequestHeader,Result}
import play.api.test.FakeRequest
import scala.concurrent.duration.Duration
import scala.concurrent.{Await,Future}
import scala.concurrent.ExecutionContext.Implicits.global

import org.overviewproject.models.ApiToken

class ApiTokenFactorySpec extends test.InAppSpecification with Mockito with JsonMatchers {
  val h = play.api.test.Helpers

  trait BaseScope extends Scope {
    implicit val timeout = new akka.util.Timeout(Duration(1000, "hours"))
    val authority = mock[Authority]
    val mockStorage = mock[ApiTokenFactory.Storage]

    val factory = new ApiTokenFactory {
      override protected val storage = mockStorage
    }

    val rightToken = ApiToken("12345", new java.sql.Timestamp(0L), "user@example.org", "foo", 4L)
    val wrongToken = ApiToken("23456", new java.sql.Timestamp(0L), "user@example.com", "bar", 5L)

    authority.apply(rightToken) returns Future(true)
    authority.apply(wrongToken) returns Future(false)

    mockStorage.loadApiToken(rightToken.token) returns Future(Some(rightToken))
    mockStorage.loadApiToken(wrongToken.token) returns Future(Some(wrongToken))

    def encode64(s: String) = {
      javax.xml.bind.DatatypeConverter.printBase64Binary(s.getBytes)
    }

    def requestToken: String = rightToken.token
    def authHeader = s"Basic ${encode64(requestToken + ":x-auth-token")}"

    def request : RequestHeader = FakeRequest().withHeaders("Authorization" -> authHeader)
    lazy val result : Either[Result, ApiToken] = Await.result(factory.loadAuthorizedApiToken(request, authority), Duration.Inf)
  }

  "ApiTokenFactory" should {
    "return Unauthorized when request has no Authentication header" in new BaseScope {
      override def request = FakeRequest()
      result must beLeft((r: Result) => {
        h.status(Future(r)) must beEqualTo(h.UNAUTHORIZED)
        h.contentAsString(Future(r)) must /("message" -> """You must set an Authorization header of 'Basic #{base64encode("YOUR-DOCSET-TOKEN:x-auth-token")}', where YOUR-DOCSET-TOKEN is a valid Overview API token.""")
      })
    }

    "return Unauthorized when Authentication header does not start with 'Basic'" in new BaseScope {
      override def request = FakeRequest().withHeaders("Authentication" -> "Blargh foo:bar")
      result must beLeft((r: Result) => h.status(Future(r)) must beEqualTo(h.UNAUTHORIZED))
    }

    "return Unauthorized when Authentication header does not have password of x-auth-token" in new BaseScope {
      override def request = FakeRequest().withHeaders("Authentication" -> s"Basic ${encode64(rightToken.token + ":some-other-password")}")
      result must beLeft((r: Result) => h.status(Future(r)) must beEqualTo(h.UNAUTHORIZED))
    }

    "return Unauthorized when Authentication header is base64 of invalid ASCII" in new BaseScope {
      override def request = FakeRequest().withHeaders("Authentication" -> "Basic R0lGODlhrwAxANUAAAAAAAAEAAgICAgMCBAQEBAU")
      result must beLeft((r: Result) => h.status(Future(r)) must beEqualTo(h.UNAUTHORIZED))
    }

    "return Unauthorized when Authentication header is invalid base64" in new BaseScope {
      override def request = FakeRequest().withHeaders("Authentication" -> "Basic $sdf")
      result must beLeft((r: Result) => h.status(Future(r)) must beEqualTo(h.UNAUTHORIZED))
    }

    "return Forbidden when the Authority returns false" in new BaseScope {
      override def requestToken = wrongToken.token
      result must beLeft((r: Result) => {
        h.status(Future(r)) must beEqualTo(h.FORBIDDEN)
        h.contentAsString(Future(r)) must /("message" -> "Your API token is valid, but it does not grant you access to this endpoint with these parameters")
      })
    }

    "return the ApiToken on success" in new BaseScope {
      result must beRight(rightToken)
    }
  }
}
