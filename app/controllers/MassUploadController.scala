package controllers

import java.util.UUID
import play.api.mvc.{ Controller, Request, RequestHeader, Result }
import org.overviewproject.tree.orm.GroupedFileUpload
import controllers.util.TransactionAction
import controllers.auth.Authorities.anyUser
import play.api.mvc.BodyParser
import org.overviewproject.tree.orm.GroupedFileUpload
import play.api.libs.iteratee.Iteratee
import org.overviewproject.tree.orm.GroupedFileUpload
import org.overviewproject.tree.orm.GroupedFileUpload
import controllers.auth.AuthorizedBodyParser
import controllers.auth.AuthorizedAction
import controllers.util.MassUploadFileIteratee
import org.overviewproject.tree.orm.FileGroup

trait MassUploadController extends Controller {

  def create(guid: UUID, lastModifiedDate: String) = TransactionAction(authorizedUploadBodyParser(guid, lastModifiedDate)) { implicit request: Request[GroupedFileUpload] =>
    val upload: GroupedFileUpload = request.body

    if (isUploadComplete(upload)) Ok
    else BadRequest
  }

  def show(guid: UUID) = AuthorizedAction(anyUser) { implicit request =>

    val result = for {
      fileGroup <- storage.findFileGroupInProgress(request.user.email)
      upload <- storage.findGroupedFileUpload(guid)
    } yield Ok.withHeaders(
        (CONTENT_LENGTH, s"${upload.size}"),
        (CONTENT_DISPOSITION, s"attachment ; filename=${upload.name}")
    )
    
    result match {
      case Some(r) => r
      case None => NotFound
    }
  }

  protected def massUploadFileIteratee(userEmail: String, request: RequestHeader, guid: UUID, lastModifiedDate: String): Iteratee[Array[Byte], Either[Result, GroupedFileUpload]]

  val storage: Storage

  trait Storage {
    def findGroupedFileUpload(guid: UUID): Option[GroupedFileUpload]
    def findFileGroupInProgress(userEmail: String): Option[FileGroup]
  }

  private def authorizedUploadBodyParser(guid: UUID, lastModifiedDate: String) =
    AuthorizedBodyParser(anyUser) { user => uploadBodyParser(user.email, guid, lastModifiedDate) }

  private def uploadBodyParser(userEmail: String, guid: UUID, lastModifiedDate: String) =
    BodyParser("Mass upload bodyparser") { request =>
      massUploadFileIteratee(userEmail, request, guid, lastModifiedDate)
    }

  private def isUploadComplete(upload: GroupedFileUpload): Boolean =
    (upload.uploadedSize == upload.size) && (upload.size > 0)
}

object MassUploadController extends MassUploadController {
  override protected def massUploadFileIteratee(userEmail: String, request: RequestHeader, guid: UUID, lastModifiedDate: String): Iteratee[Array[Byte], Either[Result, GroupedFileUpload]] =
    MassUploadFileIteratee(userEmail, request, guid, lastModifiedDate)
    
  val storage = new DatabaseStorage
  
  class DatabaseStorage extends Storage {
    override def findGroupedFileUpload(guid: UUID): Option[GroupedFileUpload] = ???
    def findFileGroupInProgress(userEmail: String): Option[FileGroup] = ???
  }
}



