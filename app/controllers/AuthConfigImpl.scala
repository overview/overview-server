package controllers

import jp.t2v.lab.play20.auth.{AuthConfig, CookieRelationResolver, RelationResolver}
import models.orm.User
import play.api.mvc.{PlainResult, Request, RequestHeader}
import play.api.mvc.Results.{Forbidden, Redirect}

trait AuthConfigImpl extends AuthConfig {
  type Id = Long
  type User = models.orm.User
  type Authority = User => Boolean

  override val idManifest : ClassManifest[Id] = classManifest[Id]

  override val sessionTimeoutInSeconds : Int = 3600

  override def resolveUser(id: Id) : Option[User] = User.findById(id)

  override def loginSucceeded(request: RequestHeader): PlainResult = {
    val uri = request.session.get("access_uri").getOrElse(routes.DocumentSetController.index.url)
    request.session - "access_uri"
    Redirect(uri)
  }

  override def logoutSucceeded(request: RequestHeader): PlainResult = Redirect(routes.WelcomeController.show)

  override def authenticationFailed(request: RequestHeader): PlainResult = {
    Redirect(routes.SessionController.new_).withSession("access_uri" -> request.uri)
  }

  override def authorizationFailed(request: RequestHeader): PlainResult = {
    Forbidden(views.html.http.forbidden())
  }

  override def authorize(user: User, authority: Authority) : Boolean = authority(user)

  override def resolver(implicit request: RequestHeader): RelationResolver[Id] = new CookieRelationResolver[Id](request)
}
