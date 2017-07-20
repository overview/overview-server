package controllers

import play.api.i18n.I18nSupport
import play.api.mvc.{ActionBuilder,AnyContent,BaseController=>PlayBaseController}

import controllers.auth.{Authority,AuthorizedAction,AuthorizedRequest,OptionallyAuthorizedAction,OptionallyAuthorizedRequest}

trait BaseController extends PlayBaseController with ControllerHelpers with I18nSupport {
  override protected def controllerComponents: ControllerComponents

  def authorizedAction = controllerComponents.authorizedAction
  def authorizedBodyParser = controllerComponents.authorizedBodyParser
  def optionallyAuthorizedAction = controllerComponents.optionallyAuthorizedAction
}
