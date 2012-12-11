package controllers.auth

import play.api.mvc.{Request,WrappedRequest}

import models.OverviewUser

class OptionallyAuthorizedRequest[A](request: Request[A], val user: Option[OverviewUser])
  extends WrappedRequest(request)
