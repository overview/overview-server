package controllers

import play.api.mvc.Controller
import play.api.libs.concurrent.Execution.Implicits._
import controllers.auth.{ AuthorizedAction, Authorities }
import scala.concurrent.Future

trait DocumentSetArchiveController extends Controller {

  import Authorities._
  
  def archive(documentSetId: Long) = AuthorizedAction.inTransaction(userViewingDocumentSet(documentSetId)){ implicit request =>
    val m = views.Magic.scopedMessages("controllers.DocumentSetArchiveController")
    
    Redirect(routes.DocumentSetController.index()).flashing("error" -> m("unsupported"))
  }
  
}


object DocumentSetArchiveController extends DocumentSetArchiveController