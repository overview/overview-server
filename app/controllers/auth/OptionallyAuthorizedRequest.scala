package controllers.auth

import play.api.mvc.{Request,WrappedRequest}

import models.OverviewUser
import models.orm.{Session, User}

case class OptionallyAuthorizedRequest[A](request: Request[A], val sessionAndUser: Option[(Session,User)])
  extends WrappedRequest(request) {

  val userSession : Option[Session] = sessionAndUser.map(_._1)
  /** TODO make this a plain User */
  val user : Option[OverviewUser] = sessionAndUser.map(_._2).map(OverviewUser(_))
}
