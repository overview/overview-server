package controllers

import play.api.mvc.{PlainResult, Request, RequestHeader}
import play.api.mvc.Results.{Forbidden, Redirect}

import models.OverviewUser

trait AuthConfigImpl {
  type User = OverviewUser
  type Authority = OverviewUser => Boolean

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
}

object AuthConfigImpl {
  private val AccessUriKey = "access_uri"
  private[controllers] val AuthUserIdKey = "AUTH_USER_ID"
}
