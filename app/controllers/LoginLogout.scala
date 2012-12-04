package controllers

import play.api.mvc._
import play.api.mvc.Cookie
import play.api.libs.Crypto

trait LoginLogout {
  self: Controller with AuthConfigImpl =>

  def gotoLoginSucceeded[A](userId: Id)(implicit request: Request[A]): PlainResult = {
    resolver.removeByUserId(userId)
    val session = resolver.store("", userId, sessionTimeoutInSeconds) // play20-auth ignores session ID
    loginSucceeded(request).withSession(session + ("sessionId" -> ""))
  }

  def gotoLogoutSucceeded[A](implicit request: Request[A]): PlainResult = {
    request.session.get("sessionId") foreach resolver.removeBySessionId
    logoutSucceeded(request).withNewSession
  }
}
