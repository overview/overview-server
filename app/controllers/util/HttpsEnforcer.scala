package controllers.util

import play.api.mvc.{BodyParser, RequestHeader}
import play.api.mvc.BodyParsers
import play.api.mvc.Results.Redirect

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
