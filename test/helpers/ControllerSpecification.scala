package controllers

import akka.util.Timeout
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.{Fragments, Step}
import play.api.libs.json.JsValue
import play.api.mvc.{AnyContent, AnyContentAsFormUrlEncoded, AnyContentAsJson, Headers, Request}
import play.api.test.{FakeApplication, FakeHeaders, FakeRequest}
import play.api.Play.{start,stop}

import controllers.auth.{AuthorizedRequest, OptionallyAuthorizedRequest}
import models.OverviewUser
import models.{Session, User}

/** A test environment for controllers.
  */
trait ControllerSpecification extends Specification with Mockito {
  override def map(fs: => Fragments) = {
    Step(start(FakeApplication())) ^ super.map(fs) ^ Step(stop)
  }

  class AugmentedRequest[T, A <: Request[T], AWithJsonBody <: Request[AnyContentAsJson], AWithFormBody <: Request[AnyContentAsFormUrlEncoded]](
    request: A,
    ctor: (FakeRequest[T] => A),
    ctorWithJsonBody: (FakeRequest[AnyContentAsJson]) => AWithJsonBody,
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

    def withHeaders(data: (String,String)*) : A = {
      val fake = toFakeRequest.withHeaders(data: _*)
      ctor(fake)
    }

    def withSession(data: (String,String)*) : A = {
      val fake = toFakeRequest.withSession(data: _*)
      ctor(fake)
    }

    def withFlash(data: (String,String)*) : A = {
      val fake = toFakeRequest.withFlash(data: _*)
      ctor(fake)
    }

    def withJsonBody(json: JsValue) : AWithJsonBody = {
      val fake = toFakeRequest.withJsonBody(json)
      ctorWithJsonBody(fake)
    }

    def withFormUrlEncodedBody(data: (String,String)*) : AWithFormBody = {
      val fake = toFakeRequest.withFormUrlEncodedBody(data: _*)
      ctorWithFormBody(fake)
    }
  }

  implicit def augmentRequest[T](r: AuthorizedRequest[T]) = new AugmentedRequest[T, AuthorizedRequest[T], AuthorizedRequest[AnyContentAsJson], AuthorizedRequest[AnyContentAsFormUrlEncoded]](
    r,
    (fakeRequest) => new AuthorizedRequest(fakeRequest, r.userSession, r.user.toUser),
    (fakeRequest) => new AuthorizedRequest(fakeRequest, r.userSession, r.user.toUser),
    (fakeRequest) => new AuthorizedRequest(fakeRequest, r.userSession, r.user.toUser)
  )
  implicit def augmentRequest[T](r: OptionallyAuthorizedRequest[T]) = new AugmentedRequest[T, OptionallyAuthorizedRequest[T], OptionallyAuthorizedRequest[AnyContentAsJson], OptionallyAuthorizedRequest[AnyContentAsFormUrlEncoded]](
    r,
    (fakeRequest) => new OptionallyAuthorizedRequest(fakeRequest, r.sessionAndUser),
    (fakeRequest) => new OptionallyAuthorizedRequest(fakeRequest, r.sessionAndUser),
    (fakeRequest) => new OptionallyAuthorizedRequest(fakeRequest, r.sessionAndUser)
  )

  def fakeUser : OverviewUser = {
    val user = User(id=2L, email="user@example.org")
    OverviewUser(user)
  }
  def fakeRequest = FakeRequest()
  def fakeAuthorizedRequest(user: OverviewUser) : AuthorizedRequest[AnyContent] = {
    new AuthorizedRequest(
      fakeRequest,
      Session(user.id, "127.0.0.1"),
      user.toUser
    )
  }
  def fakeAuthorizedRequest() : AuthorizedRequest[AnyContent] = fakeAuthorizedRequest(fakeUser)
  def fakeOptionallyAuthorizedRequest(user: Option[OverviewUser]) = {
    new OptionallyAuthorizedRequest(
      fakeRequest,
      user.map((u: OverviewUser) => (Session(u.id, "127.0.0.1"), u.toUser))
    )
  }

  implicit val timeout : Timeout = Timeout(999999, scala.concurrent.duration.MILLISECONDS)

  val h = play.api.test.Helpers // rrgh, this should be a trait
}
