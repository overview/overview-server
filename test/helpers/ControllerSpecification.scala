package controllers

import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.{Fragments, Step}
import play.api.mvc.{AnyContent, AnyContentAsFormUrlEncoded, Headers, Request}
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

  class AugmentedRequest[T, A <: Request[T], AWithFormBody <: Request[AnyContentAsFormUrlEncoded]](
    request: A,
    ctorWithFormBody: (FakeRequest[AnyContentAsFormUrlEncoded]) => AWithFormBody
  ) {

    implicit def headersToFakeHeaders(headers: Headers) : FakeHeaders = {
      FakeHeaders(headers.toMap.toSeq)
    }

    def toFakeRequest : FakeRequest[T] = FakeRequest(
      method=request.method,
      uri=request.uri,
      headers=request.headers,
      body=request.body,
      remoteAddress=request.remoteAddress,
      version=request.version,
      id=request.id,
      tags=request.tags
    )

    def withFormUrlEncodedBody(data: (String,String)*) : AWithFormBody = {
      val fakeRequestWithBody = toFakeRequest.withFormUrlEncodedBody(data: _*)
      ctorWithFormBody(fakeRequestWithBody)
    }
  }

  implicit def augmentRequest[T](r: AuthorizedRequest[T]) = new AugmentedRequest[T, AuthorizedRequest[T], AuthorizedRequest[AnyContentAsFormUrlEncoded]](r, (fakeRequest) => new AuthorizedRequest(fakeRequest, r.user))
  implicit def augmentRequest[T](r: OptionallyAuthorizedRequest[T]) = new AugmentedRequest[T, OptionallyAuthorizedRequest[T], OptionallyAuthorizedRequest[AnyContentAsFormUrlEncoded]](r, (fakeRequest) => new OptionallyAuthorizedRequest(fakeRequest, r.user))

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
