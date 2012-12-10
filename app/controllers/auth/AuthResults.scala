package controllers.auth

import play.api.Play
import play.api.Play.current
import play.api.mvc.{RequestHeader,PlainResult,Results}
import scala.util.control.Exception.catching

import models.OverviewUser

object AuthResults {
  private[auth] val RequestedUriKey = "access_uri"
  private[auth] val UserIdKey = "AUTH_USER_ID"

  /** Returns a user-not-logged-in result.
    *
    * We redirect to the login page in this case.
    */
  def authenticationFailed(request: RequestHeader): PlainResult = {
    Results.Redirect(controllers.routes.SessionController.new_).withSession(RequestedUriKey -> request.uri)
  }

  /** Returns a user-not-allowed-this-resource result.
    *
    * We return "Forbidden" in this case.
    */
  def authorizationFailed(request: RequestHeader): PlainResult = {
    Results.Forbidden(views.html.http.forbidden())
  }

  /** Returns a user-is-now-logged-in result.
    *
    * This is a Redirect to the page the user requested.
    */
  def loginSucceeded(request: RequestHeader, user: OverviewUser): PlainResult = {
    val uri = request.session.get(RequestedUriKey).getOrElse(controllers.routes.DocumentSetController.index.url)
    val newSession = request.session - RequestedUriKey + (UserIdKey -> user.id.toString)
    Results.Redirect(uri).withSession(newSession)
  }

  /** Returns a user-is-now-logged-out result.
    *
    * This is a Redirect to the home page.
    */
  def logoutSucceeded(request: RequestHeader) = {
    val uri = controllers.routes.WelcomeController.show
    val newSession = request.session - RequestedUriKey - UserIdKey
    Results.Redirect(uri).withSession(newSession)
  }
}
