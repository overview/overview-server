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

  def show(documentSetId: Long) = Action {
    Ok(views.html.DocumentSet.show())
  }
    
  def createDocumentSet() = Action { implicit request =>
    val documentSetCreationJob: DocumentSetCreationJob = queryForm.bindFromRequest.get
    documentSetCreationJob.save
      
    Redirect(routes.DocumentSetController.showDocumentSets())
  }
    
  def showDocumentSets() = Action { 
    Ok(views.html.documentSets(DocumentSet.find.all.toList, queryForm))
  }
  
  def deleteDocumentSet(documentSetId: Long) = Action {
    val documentSet = DocumentSet.find.ref(documentSetId)
    documentSet.delete()
    
    Redirect(routes.DocumentSetController.showDocumentSets())
  }
}
