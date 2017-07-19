package controllers

import akka.util.Timeout
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import play.api.{Configuration,Environment}
import play.api.i18n.MessagesApi
import play.api.libs.json.JsValue
import play.api.mvc.{AnyContent,AnyContentAsFormUrlEncoded,AnyContentAsJson,BodyParsers,DefaultActionBuilder,Headers,MessagesActionBuilder,Request}
import play.api.test.{FakeHeaders,FakeRequest,StubControllerComponentsFactory}
import play.twirl.api.Html
import scala.concurrent.ExecutionContext

import controllers.auth.{AuthConfig,AuthorizedAction,AuthorizedBodyParser,AuthorizedRequest,OptionallyAuthorizedAction,OptionallyAuthorizedRequest,SessionFactory}
import models.{Session, User}
import test.helpers.MockMessagesApi

/** A test environment for controllers.
  */
trait ControllerSpecification extends test.helpers.InAppSpecification with Mockito {
  // InAppSpecification because controllers sometimes set session variables,
  // which require the global crypto config

  implicit protected val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

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
      attrs=request.attrs
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
    (fakeRequest) => new AuthorizedRequest(fakeRequest, new MockMessagesApi, r.userSession, r.user),
    (fakeRequest) => new AuthorizedRequest(fakeRequest, new MockMessagesApi, r.userSession, r.user),
    (fakeRequest) => new AuthorizedRequest(fakeRequest, new MockMessagesApi, r.userSession, r.user)
  )
  implicit def augmentRequest[T](r: OptionallyAuthorizedRequest[T]) = new AugmentedRequest[T, OptionallyAuthorizedRequest[T], OptionallyAuthorizedRequest[AnyContentAsJson], OptionallyAuthorizedRequest[AnyContentAsFormUrlEncoded]](
    r,
    (fakeRequest) => new OptionallyAuthorizedRequest(fakeRequest, new MockMessagesApi, r.sessionAndUser),
    (fakeRequest) => new OptionallyAuthorizedRequest(fakeRequest, new MockMessagesApi, r.sessionAndUser),
    (fakeRequest) => new OptionallyAuthorizedRequest(fakeRequest, new MockMessagesApi, r.sessionAndUser)
  )

  def fakeUser : User = User(id=2L, email="user@example.org")
  def fakeRequest = FakeRequest()
  def fakeAuthorizedRequest(user: User, method: String = "GET", url: String = ""): AuthorizedRequest[AnyContent] = {
    new AuthorizedRequest(
      FakeRequest(method, url),
      new MockMessagesApi,
      Session(user.id, "127.0.0.1"),
      user
    )
  }
  def fakeAuthorizedRequest(): AuthorizedRequest[AnyContent] = fakeAuthorizedRequest(fakeUser)
  def fakeAuthorizedRequest(method: String, url: String): AuthorizedRequest[AnyContent] = fakeAuthorizedRequest(fakeUser, method, url)
  def fakeOptionallyAuthorizedRequest(user: Option[User]) = {
    new OptionallyAuthorizedRequest(
      fakeRequest,
      new MockMessagesApi,
      user.map(u => (Session(u.id, "127.0.0.1"), u))
    )
  }

  def fakeMessagesActionBuilder: MessagesActionBuilder = {
    val stubFactory = new StubControllerComponentsFactory {}
    val base = stubFactory.stubControllerComponents()
    new play.api.mvc.DefaultMessagesActionBuilderImpl(
      base.parsers.default,
      new MockMessagesApi
    )
  }

  def fakeControllerComponents: ControllerComponents = {
    val stubFactory = new StubControllerComponentsFactory {}
    val play = stubFactory.stubControllerComponents()
    val messagesApi = new MockMessagesApi

    val sessionFactory = new SessionFactory(
      // TODO make this unit-test-y instead of relying on SessionFactory's logic
      new AuthConfig { override val isMultiUser = true; override val isAdminOnlyExport = false },
      null,
      null,
      materializer.executionContext
    )

    val authorizedActionBuilder = new AuthorizedAction(sessionFactory, new BodyParsers.Default(play.parsers), messagesApi, play.executionContext)
    val authorizedBodyParserBuilder = new AuthorizedBodyParser(sessionFactory, play.parsers, play.executionContext, materializer)
    val optionallyAuthorizedActionBuilder = new OptionallyAuthorizedAction(sessionFactory, new BodyParsers.Default(play.parsers), messagesApi, play.executionContext)

    new DefaultControllerComponents(
      DefaultActionBuilder(play.actionBuilder.parser)(play.executionContext),
      play.parsers,
      messagesApi,
      play.langs,
      play.fileMimeTypes,
      authorizedActionBuilder,
      authorizedBodyParserBuilder,
      optionallyAuthorizedActionBuilder,
      play.executionContext
    )
  }

  def mockView[T: scala.reflect.ClassTag]: T = {
    mock[T]
      .defaultAnswer(_ => Html(""))
  }

  implicit val timeout: Timeout = Timeout(999999, scala.concurrent.duration.MILLISECONDS)

  val h = play.api.test.Helpers // rrgh, this should be a trait
}
