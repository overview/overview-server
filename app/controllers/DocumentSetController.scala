package controllers

import collection.JavaConversions._
import play.api.data._
import play.api.data.Forms._
import play.api.mvc.{Action,AnyContent,Request}
import java.sql.Connection
import org.squeryl.PrimitiveTypeMode._
import models.orm.{DocumentSet,DocumentSetCreationJob}

object DocumentSetController extends BaseController {
  def index() = authorizedAction(anyUser)(user => (request: Request[AnyContent], connection: Connection) => authorizedIndex(user)(request, connection))
  def show(id: Long) = authorizedAction(userOwningDocumentSet(id))(user => (request: Request[AnyContent], connection: Connection) => authorizedShow(user, id)(request, connection))
  def create() = authorizedAction(anyUser)(user => (request: Request[AnyContent], connection: Connection) => authorizedCreate(user)(request, connection))
  def delete(id: Long) = authorizedAction(userOwningDocumentSet(id))(user => (request: Request[AnyContent], connection: Connection) => authorizedDelete(user, id)(request, connection))

  private val queryForm = Form(
    mapping(
      "query" -> text
    ) ((query) => new DocumentSet(query))
      ((ds: DocumentSet) => Some((ds.query)))
  ) 

  private def authorizedIndex(user: User)(implicit request: Request[AnyContent], connection: Connection) = {
    val documentSets = user.documentSets.page(0, 20).toSeq
    Ok(views.html.DocumentSet.index(documentSets, queryForm))
  }
  
  private def authorizedShow(user: User, id: Long)(implicit request: Request[AnyContent], connection: Connection) = {
    val documentSet = user.documentSets.where(d => d.id === id).headOption
    documentSet match {
      case Some(d) => Ok(views.html.DocumentSet.show())
      case None => NotFound
    }
  }

  private def authorizedCreate(user: User)(implicit request: Request[AnyContent], connection: Connection) = {
    queryForm.bindFromRequest().fold(
      f => authorizedIndex(user),
      documentSet => {
        user.documentSets.associate(documentSet)
        documentSet.createDocumentSetCreationJob()
        Redirect(routes.DocumentSetController.index())
      }
    )
  }

  private def authorizedDelete(user: User, id: Long)(implicit request: Request[AnyContent], connection: Connection) = {
    DocumentSet.delete(id)
    Redirect(routes.DocumentSetController.index()).flashing("success" -> "FIXME translate")
  }
}
