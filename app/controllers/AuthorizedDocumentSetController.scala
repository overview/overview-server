package controllers

import play.api.data._
import play.api.data.Forms._
import play.api.mvc.{Action,AnyContent, Controller, Request}
import java.sql.Connection
import org.squeryl.PrimitiveTypeMode._

import models.orm.{DocumentSet,DocumentSetCreationJob,User}
import models.orm.DocumentSet.ImplicitHelper._
import models.{OverviewUser,OverviewDocumentSet}

trait AuthorizedDocumentSetController {
  this: Controller =>

  private val form = controllers.forms.DocumentSetForm()

  def authorizedIndex(user: OverviewUser)(implicit request: Request[AnyContent], connection: Connection) = {
    val documentSets = DocumentSet.findByUserIdOrderedByCreatedAt(user.id)
      .page(0, 20)
      .toSeq
      .withDocumentCounts
      .withCreationJobs
      .withUploadedFiles
      .map(OverviewDocumentSet.apply)
    Ok(views.html.DocumentSet.index(user, documentSets, form))
  }

  def authorizedShow(user: OverviewUser, id: Long)(implicit request: Request[AnyContent], connection: Connection) = {
    val documentSet = OverviewDocumentSet.findById(id)
    documentSet match {
      case Some(ds) => Ok(views.html.DocumentSet.show(user, ds))
      case None => NotFound
    }
  }

  def authorizedShowJson(user: OverviewUser, id: Long)(implicit request: Request[AnyContent], connection: Connection) = {
    OverviewDocumentSet.findById(id) match {
      case Some(ds) => Ok(views.json.DocumentSet.show(ds))
      case None => NotFound
    }
  }

  def authorizedCreate(user: OverviewUser)(implicit request: Request[AnyContent], connection: Connection) = {
    val m = views.Magic.scopedMessages("controllers.DocumentSetController")

    form.bindFromRequest().fold(
      f => authorizedIndex(user),
      (tuple) => {
        val documentSet = tuple._1
        val credentials = tuple._2

        val saved = documentSet.save
        User.findById(user.id).map(ormUser => saved.users.associate(ormUser))
        saved.createDocumentSetCreationJob(username=credentials.username, password=credentials.password)
        Redirect(routes.DocumentSetController.index()).flashing("success" -> m("create.success"))
      }
    )
  }

  def authorizedDelete(user: OverviewUser, id: Long)(implicit request: Request[AnyContent], connection: Connection) = {
    val m = views.Magic.scopedMessages("controllers.DocumentSetController")
    DocumentSet.delete(id)
    Redirect(routes.DocumentSetController.index()).flashing("success" -> m("delete.success"))
  }
}
