package controllers.auth

import play.api.mvc.{Request,WrappedRequest}

import models.{Session,User}

case class AuthorizedRequest[A](request: Request[A], val userSession: Session, val user: User)
  extends WrappedRequest(request) {
}
