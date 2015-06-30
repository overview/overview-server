package controllers

import akka.util.Timeout
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import play.api.libs.json.JsValue
import play.api.mvc.{AnyContent, AnyContentAsFormUrlEncoded, AnyContentAsJson, Headers, Request}
import play.api.test.{FakeHeaders, FakeRequest}

import controllers.auth.{AuthorizedRequest, OptionallyAuthorizedRequest}
import models.{Session, User}

/** A test environment for controllers.
  */
trait ControllerSpecification extends test.helpers.InAppSpecification with Mockito {
  protected implicit val executionContext = play.api.libs.concurrent.Execution.defaultContext

  class AugmentedRequest[T, A <: Request[T], AWithJsonBody <: Request[AnyContentAsJson], AWithFormBody <: Request[AnyContentAsFormUrlEncoded]](
    request: A,
    ctor: (FakeRequest[T] => A),
    ctorWithJsonBody: (FakeRequest[AnyContentAsJson]) => AWithJsonBody,
    ctorWithFormBody: (FakeRequest[AnyContentAsFormUrlEncoded]) => AWithFormBody
  ) {

    implicit def headersToFakeHeaders(headers: Headers): FakeHeaders = {
      FakeHeaders(headers.toMap.mapValues(_.head).toSeq)
    }

    def toFakeRequest: FakeRequest[T] = FakeRequest(
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
    (fakeRequest) => new AuthorizedRequest(fakeRequest, r.userSession, r.user),
    (fakeRequest) => new AuthorizedRequest(fakeRequest, r.userSession, r.user),
    (fakeRequest) => new AuthorizedRequest(fakeRequest, r.userSession, r.user)
  )
  implicit def augmentRequest[T](r: OptionallyAuthorizedRequest[T]) = new AugmentedRequest[T, OptionallyAuthorizedRequest[T], OptionallyAuthorizedRequest[AnyContentAsJson], OptionallyAuthorizedRequest[AnyContentAsFormUrlEncoded]](
    r,
    (fakeRequest) => new OptionallyAuthorizedRequest(fakeRequest, r.sessionAndUser),
    (fakeRequest) => new OptionallyAuthorizedRequest(fakeRequest, r.sessionAndUser),
    (fakeRequest) => new OptionallyAuthorizedRequest(fakeRequest, r.sessionAndUser)
  )

  def fakeUser : User = User(id=2L, email="user@example.org")
  def fakeRequest = FakeRequest()
  def fakeAuthorizedRequest(user: User, method: String = "GET", url: String = ""): AuthorizedRequest[AnyContent] = {
    new AuthorizedRequest(
      FakeRequest(method, url),
      Session(user.id, "127.0.0.1"),
      user
    )
  }
  def fakeAuthorizedRequest(): AuthorizedRequest[AnyContent] = fakeAuthorizedRequest(fakeUser)
  def fakeAuthorizedRequest(method: String, url: String): AuthorizedRequest[AnyContent] = fakeAuthorizedRequest(fakeUser, method, url)
  def fakeOptionallyAuthorizedRequest(user: Option[User]) = {
    new OptionallyAuthorizedRequest(
      fakeRequest,
      user.map(u => (Session(u.id, "127.0.0.1"), u))
    )
  }

  implicit val timeout: Timeout = Timeout(999999, scala.concurrent.duration.MILLISECONDS)

  val h = play.api.test.Helpers // rrgh, this should be a trait
}
