package controllers.auth

import play.api.mvc.{Controller,Request,RequestHeader,PlainResult}

trait LoginLogout {
  self: Controller with AuthConfigImpl =>

  private def loginSucceeded(request: RequestHeader): PlainResult = {
    val uri = request.session.get(AuthConfigImpl.AccessUriKey).getOrElse(controllers.routes.DocumentSetController.index.url)
    Redirect(uri).withSession(request.session - AuthConfigImpl.AccessUriKey)
  }

  private def logoutSucceeded(request: RequestHeader): PlainResult = Redirect(controllers.routes.WelcomeController.show)

  protected def gotoLoginSucceeded[A](userId: Long)(implicit request: Request[A]): PlainResult = {
    loginSucceeded(request).withSession(request.session + (AuthConfigImpl.AuthUserIdKey -> userId.toString))
  }

  protected def gotoLogoutSucceeded[A](implicit request: Request[A]): PlainResult = {
    logoutSucceeded(request).withSession(session - AuthConfigImpl.AuthUserIdKey)
  }
}
