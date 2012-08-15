package controllers

import collection.JavaConversions._

import play.api.data._
import play.api.data.Forms._
import play.api.mvc.{Action,Controller}
import models.{DocumentSet, DocumentSetCreationJob}

object DocumentSetController extends Controller {
  val queryForm = Form(
    mapping(
      "query" -> text
    ) ((query) => new DocumentSetCreationJob(query))
      ((job: DocumentSetCreationJob) => Some((job.query)))
  ) 

  def index() = Action {
    // FIXME make per-user
    val documentSets = DocumentSet.find.orderBy("query").findList
    val documentSetCreationJobs = DocumentSetCreationJob.find.orderBy("query").findList
    Ok(views.html.DocumentSet.index(documentSets, documentSetCreationJobs, queryForm))
  }

  def show(documentSetId: Long) = Action {
    // FIXME check user has access to document set
    val documentSet = DocumentSet.find.byId(documentSetId) // for potential 404 error
    Ok(views.html.DocumentSet.show())
  }

  def create() = Action { implicit request =>
    val documentSetCreationJob: DocumentSetCreationJob = queryForm.bindFromRequest.get
    documentSetCreationJob.save

    Redirect(routes.DocumentSetController.index())
  }
}
