package controllers.auth

import play.api.Play
import play.api.Play.current
import play.api.mvc.{RequestHeader,Result,Results}
import scala.util.control.Exception.catching

import models.orm.Session

object AuthResults {
  private[auth] val RequestedUriKey = "access_uri"
  private[auth] val SessionIdKey = "AUTH_SESSION_ID"

  /** Returns a there-is-no-session result.
    *
    * We redirect to the login page in this case.
    */
  def authenticationFailed(request: RequestHeader): Result = {
    Results.Redirect(controllers.routes.SessionController.new_).withSession(RequestedUriKey -> request.uri)
  }

  /** Returns a user-not-allowed-this-resource result.
    *
    * We return "Forbidden" in this case.
    */
  def authorizationFailed(request: RequestHeader): Result = {
    Results.Forbidden(views.html.http.forbidden())
  }

  /** Returns a session-has-just-been-created result.
    *
    * This is a Redirect to the page the user requested.
    */
  def loginSucceeded(request: RequestHeader, session: Session): Result = {
    val uri = request.session.get(RequestedUriKey).getOrElse(controllers.routes.DocumentSetController.index().url)
    val newSession = request.session - RequestedUriKey + (SessionIdKey -> session.id.toString)
    Results.Redirect(uri).withSession(newSession)
  }

  /** Returns a session-has-just-been-deleted result.
    *
    * This is a Redirect to the home page.
    */
  def logoutSucceeded(request: RequestHeader) = {
    val uri = controllers.routes.WelcomeController.show
    val newSession = request.session - RequestedUriKey - SessionIdKey
    Results.Redirect(uri).withSession(newSession)
  }
}
