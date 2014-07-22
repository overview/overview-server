package controllers.auth

import scala.concurrent.Future

import org.overviewproject.models.ApiToken
import models.OverviewUser

/** Determines whether the given user/apiToken has access. */
trait Authority {
  def apply(user: OverviewUser): Boolean

  def apply(apiToken: ApiToken): Future[Boolean]
}
