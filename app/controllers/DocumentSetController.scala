package controllers

import collection.JavaConversions._
import play.api.data._
import play.api.data.Forms._
import play.api.mvc.{Action,AnyContent,Request}
import java.sql.Connection
import org.squeryl.PrimitiveTypeMode._
import models.DocumentSet
import models.orm.DocumentSetCreationJob
import models.orm.Schema

object DocumentSetController extends BaseController {
  def index() = authorizedAction(anyUser)(user => (request: Request[AnyContent], connection: Connection) => authorizedIndex(user)(request, connection))
  def show(id: Long) = authorizedAction(userOwningDocumentSet(id))(user => (request: Request[AnyContent], connection: Connection) => authorizedShow(user, id)(request, connection))
  def create() = authorizedAction(anyUser)(user => (request: Request[AnyContent], connection: Connection) => authorizedCreate(user)(request, connection))

  private val queryForm = Form(
    mapping(
      "query" -> text
    ) ((query) => new DocumentSetCreationJob(query))
      ((job: DocumentSetCreationJob) => Some((job.query)))
  ) 

  private def authorizedIndex(user: User)(implicit request: Request[AnyContent], connection: Connection) = {
    val documentSets = user.documentSets.page(0, 20).toSeq
    val documentSetCreationJobs = user.documentSetCreationJobs.toSeq
    Ok(views.html.DocumentSet.index(documentSets, documentSetCreationJobs, queryForm))
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
      job => {
        user.documentSetCreationJobs.associate(job)
        Redirect(routes.DocumentSetController.index())
      }
    )
  }
}
