package controllers.util

import play.api.mvc.{Action, AnyContent, BodyParser, RequestHeader, Request, Result}
import play.api.mvc.BodyParsers
import play.api.mvc.Results.Redirect

trait HttpsEnforcer {
  def HttpsAction(f: Request[AnyContent] => Result): Action[AnyContent] = {
    HttpsAction(BodyParsers.parse.anyContent)(f)
  }
    
  
  def HttpsAction[A](bp: BodyParser[A])(f: Request[A] => Result): Action[A] = {
    Action(bp) { request =>
      request.headers.get("x-forwarded-proto") match {
        case Some("https") => f(request)
        case Some("http") => Redirect("https://" + request.host + request.uri)
        case _ => f(request)
      }
    }
  }
}

object HttpsEnforcer {
  private def requestIsHttpAndShouldBeHttps(rh: RequestHeader) = {
    rh.headers.get("x-forwarded-proto") match {
      case Some("https") => false
      case Some("http") => true
      case _ => false // No header -- e.g., on a dev machine
    }
  }

  def HttpsBodyParser[A](bp: BodyParser[A]) : BodyParser[A] = {
    BodyParsers.parse.when(
      !requestIsHttpAndShouldBeHttps(_),
      bp,
      { rh: RequestHeader => Redirect("https://" + rh.host + rh.uri) }
    )
  }
}
