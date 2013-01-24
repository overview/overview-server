package controllers

import java.sql.Connection
import play.api.mvc.Controller
import org.overviewproject.postgres.SquerylEntrypoint._ // TODO: remove this!
import org.overviewproject.tree.orm.DocumentSetCreationJobType.DocumentCloudJob

import controllers.auth.{AuthorizedAction,Authorities}
import models.orm.{DocumentSet,User} // TODO: remove this!
import models.orm.DocumentSet.ImplicitHelper._ // TODO: remove this!
import models.{ OverviewDocumentSet, OverviewDocumentSetCreationJob }

trait DocumentSetController extends Controller {
  import Authorities._

  private val form = controllers.forms.DocumentSetForm()

  def index() = AuthorizedAction(anyUser) { implicit request =>
    val documentSets = DocumentSet.findByUserIdOrderedByCreatedAt(request.user.id)
      .page(0, 20)
      .toSeq
      .withDocumentCounts
      .withCreationJobs
      .withUploadedFiles
      .map(OverviewDocumentSet.apply)

    Ok(views.html.DocumentSet.index(request.user, documentSets, form))
  }

  def show(id: Long) = AuthorizedAction(userOwningDocumentSet(id)) { implicit request =>
    val documentSet = OverviewDocumentSet.findById(id)
    documentSet match {
      case Some(ds) => Ok(views.html.DocumentSet.show(request.user, ds))
      case None => NotFound
    }
  }

  def showJson(id: Long) = AuthorizedAction(userOwningDocumentSet(id)) { implicit request =>
    OverviewDocumentSet.findById(id) match {
      case Some(ds) => Ok(views.json.DocumentSet.show(ds))
      case None => NotFound
    }
  }

  def create() = AuthorizedAction(anyUser) { implicit request =>
    val m = views.Magic.scopedMessages("controllers.DocumentSetController")

    form.bindFromRequest().fold(
      f => index()(request),
      (tuple) => {
        val documentSet = tuple._1
        val credentials = tuple._2

        val saved = documentSet.save
        User.findById(request.user.id).map(ormUser => saved.users.associate(ormUser))
        saved.createDocumentSetCreationJob(username=credentials.username, password=credentials.password)
        Redirect(routes.DocumentSetController.index()).flashing("success" -> m("create.success"))
      }
    )
  }

  def delete(id: Long) = AuthorizedAction(userOwningDocumentSet(id)) { implicit request =>
    val m = views.Magic.scopedMessages("controllers.DocumentSetController")
    OverviewDocumentSet.delete(id)
    Redirect(routes.DocumentSetController.index()).flashing("success" -> m("delete.success"))
  }
}

object DocumentSetController extends DocumentSetController
