package controllers

import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.{Fragments, Step}
import play.api.mvc.{AnyContent,AnyContentAsFormUrlEncoded,Headers}
import play.api.test.{FakeApplication, FakeHeaders, FakeRequest}
import play.api.Play.{start,stop}

import controllers.auth.{AuthorizedRequest, OptionallyAuthorizedRequest}
import models.OverviewUser

/** A test environment for controllers.
  */
trait ControllerSpecification extends Specification with Mockito {
  override def map(fs: => Fragments) = {
    Step(start(FakeApplication())) ^ super.map(fs) ^ Step(stop)
  }

  class AugmentedAuthorizedRequest[A](authorizedRequest: AuthorizedRequest[A]) {
    implicit def headersToFakeHeaders(headers: Headers) : FakeHeaders = {
      FakeHeaders(headers.toMap.toSeq)
    }

    def toFakeRequest : FakeRequest[A] = FakeRequest(
      method=authorizedRequest.method,
      uri=authorizedRequest.uri,
      headers=authorizedRequest.headers,
      body=authorizedRequest.body,
      remoteAddress=authorizedRequest.remoteAddress,
      version=authorizedRequest.version,
      id=authorizedRequest.id,
      tags=authorizedRequest.tags)

    def withFormUrlEncodedBody(data: (String,String)*) : AuthorizedRequest[AnyContentAsFormUrlEncoded] = {
      new AuthorizedRequest(
        toFakeRequest.withFormUrlEncodedBody(data: _*),
        authorizedRequest.user
      )
    }
  }
  implicit def authorizedRequestToAugmentedAuthorizedRequest[A](r: AuthorizedRequest[A]) = new AugmentedAuthorizedRequest(r)

  def fakeUser : OverviewUser = {
    val ret = mock[OverviewUser]
    ret.email returns "user@example.org"
    ret.isAdministrator returns false
    ret
  }
  def fakeRequest = FakeRequest()
  def fakeAuthorizedRequest(user: OverviewUser) : AuthorizedRequest[AnyContent] = new AuthorizedRequest(fakeRequest, user)
  def fakeAuthorizedRequest() : AuthorizedRequest[AnyContent] = fakeAuthorizedRequest(fakeUser)
  def fakeOptionallyAuthorizedRequest(user: Option[OverviewUser]) = new OptionallyAuthorizedRequest(fakeRequest, user)

  implicit val timeout = akka.util.Timeout(999999)

  val h = play.api.test.Helpers // rrgh, this should be a trait
}
