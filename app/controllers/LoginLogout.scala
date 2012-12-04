package controllers

import play.api.mvc.{Controller,Request,PlainResult}

trait LoginLogout {
  self: Controller with AuthConfigImpl =>

  def gotoLoginSucceeded[A](userId: Long)(implicit request: Request[A]): PlainResult = {
    loginSucceeded(request).withSession(request.session + (AuthConfigImpl.AuthUserIdKey -> userId.toString))
  }

  def gotoLogoutSucceeded[A](implicit request: Request[A]): PlainResult = {
    logoutSucceeded(request).withSession(session - AuthConfigImpl.AuthUserIdKey)
  }
}
