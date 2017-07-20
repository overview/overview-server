package controllers

import com.google.inject.ImplementedBy
import javax.inject.Inject
import play.api.i18n.{Langs,MessagesApi}
import play.api.http.FileMimeTypes
import play.api.mvc.{ActionBuilder,AnyContent,ControllerComponents=>PlayControllerComponents,DefaultActionBuilder,PlayBodyParsers,Request}
import scala.concurrent.ExecutionContext

import controllers.auth.{AuthorizedAction,AuthorizedBodyParser,OptionallyAuthorizedAction}

@ImplementedBy(classOf[DefaultControllerComponents])
trait ControllerComponents extends PlayControllerComponents {
  def authorizedAction: AuthorizedAction
  def authorizedBodyParser: AuthorizedBodyParser
  def optionallyAuthorizedAction: OptionallyAuthorizedAction
}

case class DefaultControllerComponents @Inject() (
  actionBuilder: DefaultActionBuilder,
  parsers: PlayBodyParsers,
  messagesApi: MessagesApi,
  langs: Langs,
  fileMimeTypes: FileMimeTypes,
  authorizedAction: AuthorizedAction,
  authorizedBodyParser: AuthorizedBodyParser,
  optionallyAuthorizedAction: OptionallyAuthorizedAction,
  executionContext: ExecutionContext
) extends ControllerComponents
