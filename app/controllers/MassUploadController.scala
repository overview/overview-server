package controllers

import java.util.UUID
import play.api.mvc.{Controller, Request, RequestHeader, Result}
import org.overviewproject.tree.orm.GroupedFileUpload
import controllers.util.TransactionAction
import controllers.auth.Authorities.anyUser
import play.api.mvc.BodyParser
import org.overviewproject.tree.orm.GroupedFileUpload
import play.api.libs.iteratee.Iteratee
import org.overviewproject.tree.orm.GroupedFileUpload
import org.overviewproject.tree.orm.GroupedFileUpload
import controllers.auth.AuthorizedBodyParser

trait MassUploadController extends Controller {
  def create(guid: UUID, lastModifiedDate: String) = TransactionAction(authorizedUploadBodyParser(guid, lastModifiedDate)) { implicit request: Request[GroupedFileUpload] =>
    val upload: GroupedFileUpload = request.body
    
    if (isUploadComplete(upload)) Ok
    else BadRequest
  }

  def massUploadFileIteratee(guid: UUID, lastModifiedDate: String): Iteratee[Array[Byte], Either[Result, GroupedFileUpload]]
  
  private def authorizedUploadBodyParser(guid: UUID, lastModifiedDate: String) =
    AuthorizedBodyParser(anyUser) { user => uploadBodyParser(guid, lastModifiedDate) }
  
  private def uploadBodyParser(guid: UUID, lastModifiedDate: String) = BodyParser("Mass upload bodyparser") { request =>
    massUploadFileIteratee(guid, lastModifiedDate)
  }
  
  private def isUploadComplete(upload: GroupedFileUpload): Boolean = 
    (upload.uploadedSize == upload.size) && (upload.size > 0) 
}

object MassUploadController extends MassUploadController {
  def massUploadFileIteratee(guid: UUID, lastModifiedDate: String): Iteratee[Array[Byte], Either[Result, GroupedFileUpload]] =
    ???
}



