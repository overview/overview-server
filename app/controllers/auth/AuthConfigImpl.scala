package controllers.auth

import play.api.mvc.{PlainResult, Request, RequestHeader}
import play.api.mvc.Results.{Forbidden, Redirect}

import models.OverviewUser

trait AuthConfigImpl {
  type User = OverviewUser
}

object AuthConfigImpl {
  private[controllers] val AccessUriKey = "access_uri"
  private[controllers] val AuthUserIdKey = "AUTH_USER_ID"
}
