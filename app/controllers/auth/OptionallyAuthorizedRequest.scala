package controllers.auth

import play.api.mvc.{Request,WrappedRequest}

import models.{Session,User}

case class OptionallyAuthorizedRequest[A](request: Request[A], val sessionAndUser: Option[(Session,User)])
  extends WrappedRequest(request) {

  val userSession: Option[Session] = sessionAndUser.map(_._1)
  val user: Option[User] = sessionAndUser.map(_._2)
}
