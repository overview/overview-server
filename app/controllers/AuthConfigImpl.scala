package controllers

import jp.t2v.lab.play20.auth.{AuthConfig, CookieRelationResolver, RelationResolver}
import play.api.mvc.{PlainResult, Request, RequestHeader}
import play.api.mvc.Results.{Forbidden, Redirect}

import models.OverviewUser

trait AuthConfigImpl extends AuthConfig {
  type Id = Long
  type User = OverviewUser
  type Authority = OverviewUser => Boolean

  override val idManifest : ClassManifest[Id] = classManifest[Id]

  override val sessionTimeoutInSeconds : Int = 3600

  override def resolveUser(id: Id) : Option[OverviewUser] = {
    OverviewUser.findById(id)
  }

  override def loginSucceeded(request: RequestHeader): PlainResult = {
    val uri = request.session.get(AuthConfigImpl.AccessUriKey).getOrElse(routes.DocumentSetController.index.url)
    Redirect(uri).withSession(request.session - AuthConfigImpl.AccessUriKey)
  }

  override def logoutSucceeded(request: RequestHeader): PlainResult = Redirect(routes.WelcomeController.show)

  override def authenticationFailed(request: RequestHeader): PlainResult = {
    Redirect(routes.SessionController.new_).withSession(AuthConfigImpl.AccessUriKey -> request.uri)
  }

  override def authorizationFailed(request: RequestHeader): PlainResult = {
    Forbidden(views.html.http.forbidden())
  }

  override def authorize(user: User, authority: Authority) : Boolean = throw new Exception("This method should never be called")

  override def resolver(implicit request: RequestHeader): RelationResolver[Id] = new CookieRelationResolver[Id](request)
}

object AuthConfigImpl {
  private val AccessUriKey = "access_uri"
}
