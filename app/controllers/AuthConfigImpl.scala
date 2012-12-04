package controllers

import jp.t2v.lab.play20.auth.{CookieRelationResolver, RelationResolver}
import play.api.mvc.{PlainResult, Request, RequestHeader}
import play.api.mvc.Results.{Forbidden, Redirect}

import models.OverviewUser

trait AuthConfigImpl {
  type Id = Long
  type User = OverviewUser
  type Authority = OverviewUser => Boolean

  val idManifest : ClassManifest[Id] = classManifest[Id]

  val sessionTimeoutInSeconds : Int = 3600

  def resolveUser(id: Id) : Option[OverviewUser] = {
    OverviewUser.findById(id)
  }

  def loginSucceeded(request: RequestHeader): PlainResult = {
    val uri = request.session.get(AuthConfigImpl.AccessUriKey).getOrElse(routes.DocumentSetController.index.url)
    Redirect(uri).withSession(request.session - AuthConfigImpl.AccessUriKey)
  }

  def logoutSucceeded(request: RequestHeader): PlainResult = Redirect(routes.WelcomeController.show)

  def authenticationFailed(request: RequestHeader): PlainResult = {
    Redirect(routes.SessionController.new_).withSession(AuthConfigImpl.AccessUriKey -> request.uri)
  }

  def authorizationFailed(request: RequestHeader): PlainResult = {
    Forbidden(views.html.http.forbidden())
  }

  def authorize(user: User, authority: Authority) : Boolean = throw new Exception("This method should never be called")

  def resolver(implicit request: RequestHeader): RelationResolver[Id] = new CookieRelationResolver[Id](request)
}

object AuthConfigImpl {
  private val AccessUriKey = "access_uri"
}
