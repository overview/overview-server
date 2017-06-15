package controllers

import play.api.i18n.{I18nSupport,MessagesApi}
import play.api.mvc.{Controller=>PlayController}

class Controller(override val messagesApi: MessagesApi) extends PlayController with ControllerHelpers with I18nSupport
