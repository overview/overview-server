package controllers.util

import play.api.mvc._
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

