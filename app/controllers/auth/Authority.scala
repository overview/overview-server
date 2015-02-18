package controllers.auth

import scala.concurrent.Future

import org.overviewproject.models.ApiToken
import models.User

/** Determines whether the given user/apiToken has access. */
trait Authority {
  def apply(user: User): Boolean
  def apply(apiToken: ApiToken): Future[Boolean]
}
