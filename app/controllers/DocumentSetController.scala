package controllers

import java.sql.Connection
import play.api.mvc.{AnyContent, Request}

object DocumentSetController extends BaseController with AuthorizedDocumentSetController {

  def index() = authorizedAction(anyUser)(user => authorizedIndex(user)(_: Request[AnyContent], _: Connection))
  def show(id: Long) = authorizedAction(userOwningDocumentSet(id))(user => authorizedShow(user, id)(_: Request[AnyContent], _: Connection))
  def showJson(id: Long) = authorizedAction(userOwningDocumentSet(id))(user => authorizedShowJson(user, id)(_: Request[AnyContent], _: Connection))
  def create() = authorizedAction(anyUser)(user => authorizedCreate(user)(_: Request[AnyContent], _: Connection))
  def delete(id: Long) = authorizedAction(userOwningDocumentSet(id))(user => authorizedDelete(user, id)(_: Request[AnyContent], _: Connection))
  
}