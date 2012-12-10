package controllers.auth

import models.OverviewUser

trait Authority extends Function1[OverviewUser,Boolean]
