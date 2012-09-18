package controllers

import play.api.data._
import play.api.data.Forms._
import play.api.mvc.{Action,AnyContent, Controller, Request}
import java.sql.Connection
import org.squeryl.PrimitiveTypeMode._
import models.orm.{DocumentSet,DocumentSetCreationJob, User}
import models.orm.DocumentSet.ImplicitHelper._

trait AuthorizedDocumentSetController {
  this: Controller =>

  private val form = controllers.forms.DocumentSetForm()

  def authorizedIndex(user: User)(implicit request: Request[AnyContent], connection: Connection) = { 
    val documentSets = user.documentSets.page(0, 20).toSeq.withDocumentCounts.withCreationJobs
    Ok(views.html.DocumentSet.index(user, documentSets, form))
  }

  def authorizedShow(user: User, id: Long)(implicit request: Request[AnyContent], connection: Connection) = {
    val documentSet = user.documentSets.where(d => d.id === id).headOption
    documentSet match {
      case Some(ds) => Ok(views.html.DocumentSet.show(user, ds))
      case None => NotFound
    }
  }

  def authorizedShowJson(user: User, id: Long)(implicit request: Request[AnyContent], connection: Connection) = {
    val documentSet = user.documentSets.where(d => d.id === id).toSeq.withCreationJobs.headOption
    documentSet match {
      case Some(ds) => Ok(views.json.DocumentSet.show(ds))
      case None => NotFound
    }
  }

  def authorizedCreate(user: User)(implicit request: Request[AnyContent], connection: Connection) = {
    form.bindFromRequest().fold(
      f => authorizedIndex(user),
      (tuple) => {
        val documentSet = tuple._1
        val credentials = tuple._2

        val saved = documentSet.save()
        saved.users.associate(user)
        saved.createDocumentSetCreationJob(username=credentials.username, password=credentials.password)
        Redirect(routes.DocumentSetController.index()).flashing("success" -> "controllers.DocumentSetController.create.success")
      }
    )
  }

  def authorizedDelete(user: User, id: Long)(implicit request: Request[AnyContent], connection: Connection) = {
    DocumentSet.delete(id)
    Redirect(routes.DocumentSetController.index()).flashing("success" -> "controllers.DocumentSetController.delete.success")
  }
}
