package controllers.auth

import play.api.i18n.MessagesApi
import play.api.mvc.{MessagesRequest,Request}

import models.{Session,User}

case class AuthorizedRequest[A](
  request: Request[A],
  _messagesApi: MessagesApi,
  userSession: Session,
  user: User
) extends MessagesRequest[A](request, _messagesApi)
