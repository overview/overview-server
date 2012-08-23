package controllers

import collection.JavaConversions._
import play.api.data._
import play.api.data.Forms._
import play.api.mvc.{Action,AnyContent,Request}
import java.sql.Connection
import org.squeryl.PrimitiveTypeMode._
import models.orm.{DocumentSet,DocumentSetCreationJob}
import models.orm.DocumentSet.ImplicitHelper._

object DocumentSetController extends BaseController {
  def index() = authorizedAction(anyUser)(user => this.authorizedIndex(user)(_: Request[AnyContent], _: Connection))
  def show(id: Long) = authorizedAction(userOwningDocumentSet(id))(user => authorizedShow(user, id)(_: Request[AnyContent], _: Connection))
  def showJson(id: Long) = authorizedAction(userOwningDocumentSet(id))(user => authorizedShowJson(user, id)(_: Request[AnyContent], _: Connection))
  def create() = authorizedAction(anyUser)(user => authorizedCreate(user)(_: Request[AnyContent], _: Connection))
  def delete(id: Long) = authorizedAction(userOwningDocumentSet(id))(user => authorizedDelete(user, id)(_: Request[AnyContent], _: Connection))
  
  private val queryForm = Form(
    "query" -> text
  ) 

  private def authorizedIndex(user: User)(implicit request: Request[AnyContent], connection: Connection) = {
    val documentSets = user.documentSets.page(0, 20).toSeq.withDocumentCounts.withCreationJobs
    Ok(views.html.DocumentSet.index(documentSets, queryForm))
  }
  
  private def authorizedShow(user: User, id: Long)(implicit request: Request[AnyContent], connection: Connection) = {
    val documentSet = user.documentSets.where(d => d.id === id).headOption
    documentSet match {
      case Some(d) => Ok(views.html.DocumentSet.show())
      case None => NotFound
    }
  }

  private def authorizedShowJson(user: User, id: Long)(implicit request: Request[AnyContent], connection: Connection) = {
    val documentSet = user.documentSets.where(d => d.id === id).toSeq.withCreationJobs.headOption
    documentSet match {
      case Some(ds) => Ok(views.json.DocumentSet.show(ds))
      case None => NotFound
    }
  }

  private def authorizedCreate(user: User)(implicit request: Request[AnyContent], connection: Connection) = {
    queryForm.bindFromRequest().fold(
      f => authorizedIndex(user),
      query => {
        val documentSet = user.createDocumentSet(query)
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
