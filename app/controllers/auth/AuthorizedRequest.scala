package controllers.auth

import play.api.mvc.{Request,WrappedRequest}

import models.OverviewUser

case class AuthorizedRequest[A](request: Request[A], val user: OverviewUser)
  extends WrappedRequest(request)
