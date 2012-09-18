package controllers

import play.api.mvc.{Request,PlainResult}
import play.api.mvc.Results.{Redirect,Forbidden}
import jp.t2v.lab.play20.auth.AuthConfig

import models.orm.User

trait AuthConfigImpl extends AuthConfig {
  type Id = Long
  type User = models.orm.User
  type Authority = User => Boolean

  override val idManifest : ClassManifest[Id] = classManifest[Id]

  override val sessionTimeoutInSeconds : Int = 3600

  override def resolveUser(id: Id) : Option[User] = User.findById(id)

  override def loginSucceeded[A](request: Request[A]): PlainResult = {
    val uri = request.session.get("access_uri").getOrElse(routes.DocumentSetController.index.url)
    request.session - "access_uri"
    Redirect(uri)
  }

  override def logoutSucceeded[A](request: Request[A]): PlainResult = Redirect(routes.WelcomeController.show)

  override def authenticationFailed[A](request: Request[A]): PlainResult = {
    Redirect(routes.SessionController.new_).withSession("access_uri" -> request.uri)
  }

  override def authorizationFailed[A](request: Request[A]): PlainResult = {
    Forbidden(views.html.http.forbidden())
  }

  override def authorize(user: User, authority: Authority) : Boolean = authority(user)
}
