package controllers.api

import com.google.inject.ImplementedBy
import javax.inject.Inject
import play.api.mvc.PlayBodyParsers
import scala.concurrent.ExecutionContext

import controllers.auth.ApiAuthorizedAction

@ImplementedBy(classOf[DefaultApiControllerComponents])
trait ApiControllerComponents {
  def apiAuthorizedAction: ApiAuthorizedAction
  def executionContext: ExecutionContext
  def parsers: PlayBodyParsers
}

case class DefaultApiControllerComponents @Inject() (
  apiAuthorizedAction: ApiAuthorizedAction,
  executionContext: ExecutionContext,
  parsers: PlayBodyParsers
) extends ApiControllerComponents
