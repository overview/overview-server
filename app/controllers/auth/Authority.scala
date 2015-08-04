package controllers.auth

import scala.concurrent.Future

import com.overviewdocs.models.ApiToken
import models.User

/** Determines whether the given user/apiToken has access. */
trait Authority {
  def apply(user: User): Future[Boolean]
  def apply(apiToken: ApiToken): Future[Boolean]
}
