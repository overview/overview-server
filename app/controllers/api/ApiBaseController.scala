package controllers.api

import play.api.mvc.PlayBodyParsers
import scala.concurrent.ExecutionContext

import controllers.auth.ApiAuthorizedAction

/** Common API controller methods, modeled after Play's BaseController.
  *
  * API controllers don't have i18n.
  */
trait ApiBaseController extends ApiControllerHelpers {
  protected def controllerComponents: ApiControllerComponents

  def parse: PlayBodyParsers = controllerComponents.parsers
  def defaultExecutionContext: ExecutionContext = controllerComponents.executionContext
  def apiAuthorizedAction: ApiAuthorizedAction = controllerComponents.apiAuthorizedAction
}
