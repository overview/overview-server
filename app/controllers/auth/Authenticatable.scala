package controllers.auth

import models.OverviewUser

trait Authenticatable {
  type Authority = OverviewUser => Boolean
}
