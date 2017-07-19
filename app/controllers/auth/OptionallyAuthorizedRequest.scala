package controllers.auth

import play.api.i18n.MessagesApi
import play.api.mvc.{MessagesRequest,Request}

import models.{Session,User}

case class OptionallyAuthorizedRequest[A](
  request: Request[A],
  _messagesApi: MessagesApi,
  sessionAndUser: Option[(Session,User)]
) extends MessagesRequest(request, _messagesApi) {

  val userSession: Option[Session] = sessionAndUser.map(_._1)
  val user: Option[User] = sessionAndUser.map(_._2)
}
