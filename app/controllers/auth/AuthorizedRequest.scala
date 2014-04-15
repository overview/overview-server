package controllers.auth

import play.api.mvc.{Request,WrappedRequest}

import models.OverviewUser
import models.orm.{Session, User}

case class AuthorizedRequest[A](request: Request[A], val userSession: Session, private val plainUser: User)
  extends WrappedRequest(request) {

  /** TODO make this a plain User */
  val user : OverviewUser = OverviewUser(plainUser)
}
