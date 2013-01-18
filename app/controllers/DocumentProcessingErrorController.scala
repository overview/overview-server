package controllers

import play.api.mvc.Controller
import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.userOwningDocumentSet
import org.overviewproject.tree.orm.DocumentProcessingError

trait DocumentProcessingErrorController extends Controller {

  def index(documentSetId: Long) = AuthorizedAction(userOwningDocumentSet(documentSetId)) { implicit request =>
    val errors = findDocumentProcessingErrors(documentSetId)
    
    Ok("hi")//views.json.DocumentProcessingError.show(errors))
  }
  
  protected def findDocumentProcessingErrors(documentSetId: Long): Seq[DocumentProcessingError]
}

object DocumentProcessingErrorController extends DocumentProcessingErrorController {
  override protected def findDocumentProcessingErrors(documentSetId: Long): Seq[DocumentProcessingError] = {
    Nil    
  }
}