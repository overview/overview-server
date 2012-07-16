package controllers

import play.api.mvc.{Action,Controller}
import models.DocumentSetCreationJob
import collection.JavaConversions._

object Application  extends Controller {
  
  def index() = Action {
    Ok(views.html.index("The Overview Project"))
  }
  
  def showJobs() = Action {
    Ok(views.html.viewJobs(DocumentSetCreationJob.find.all.toList))
  }
  
  
}
