package controllers.auth

import java.nio.charset.Charset
import javax.xml.bind.DatatypeConverter
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc.{RequestHeader,Result,Results}
import scala.concurrent.Future
import scala.util.control.Exception.catching

import org.overviewproject.models.ApiToken

/** Authorizes API tokens.
  *
  * This involves a few tasks:
  *
  * 1. Fetch the ApiToken from the database (not there? authentication failed)
  * 2. Check if the user is authorized for the task at hand (no? authorization failed)
  */
trait ApiTokenFactory {
  protected val storage: ApiTokenFactory.Storage

  private def unauthenticated: Result = {
    Results.Unauthorized(views.json.api.auth.unauthenticated())
  }

  private def forbidden: Result = {
    Results.Forbidden(views.json.api.auth.forbidden())
  }

  /** Returns either a Result (no access) or an ApiToken (access). */
  def loadAuthorizedApiToken(request: RequestHeader, authority: Authority) : Future[Either[Result,ApiToken]] = {
    request.headers.get("Authorization").toRight(unauthenticated)
      .right.flatMap(ApiTokenFactory.authorizationHeaderToToken(_).toRight(unauthenticated))
      match {
        case Left(result) => Future(Left(result))
        case Right(tokenString) => {
          storage.loadApiToken(tokenString).flatMap(_ match {
            case None => Future(Left(unauthenticated))
            case Some(apiToken) => {
              authority(apiToken).map((allowed: Boolean) => allowed match {
                case true => Right(apiToken)
                case false => Left(forbidden)
              })
            }
          })
        }
      }
  }
}

object ApiTokenFactory {
  private val ascii = Charset.forName("US-ASCII")

  private def getEncodedUsernameAndPassword(s: String) : Option[String] = {
    if (s.startsWith("Basic ")) Some(s.substring(6)) else None
  }

  private def decode64(s: String) : Option[String] = {
    catching(classOf[IllegalArgumentException]).opt(DatatypeConverter.parseBase64Binary(s))
      .map((b: Array[Byte]) => new String(b, ascii))
  }

  private def getTokenString(usernameAndPassword: String) : Option[String] = {
    if (usernameAndPassword.contains(':')) {
      val parts = usernameAndPassword.split(":", 2)
      if (parts(1).equals("x-auth-token")) {
        Some(parts(0))
      } else {
        None
      }
    } else {
      None
    }
  }

  private def authorizationHeaderToToken(s: String) : Option[String] = {
    val ret = getEncodedUsernameAndPassword(s)
      .flatMap(decode64(_))
      .flatMap(getTokenString(_))
    ret
  }

  trait Storage {
    def loadApiToken(token: String) : Future[Option[ApiToken]]
  }
}
