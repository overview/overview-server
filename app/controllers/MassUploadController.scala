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
import models.orm.finders.FileGroupFinder
import models.orm.finders.GroupedFileUploadFinder

trait MassUploadController extends Controller {

  def create(guid: UUID, lastModifiedDate: String) = TransactionAction(authorizedUploadBodyParser(guid, lastModifiedDate)) { implicit request: Request[GroupedFileUpload] =>
    val upload: GroupedFileUpload = request.body

    if (isUploadComplete(upload)) Ok
    else BadRequest
  }

  def show(guid: UUID) = AuthorizedAction(anyUser) { implicit request =>

    val result = for {
      fileGroup <- storage.findCurrentFileGroup(request.user.email)
      upload <- storage.findGroupedFileUpload(fileGroup.id, guid)
    } yield {
      if (isUploadComplete(upload)) Ok.withHeaders(showRequestHeaders(upload): _*)
      else PartialContent.withHeaders(showRequestHeaders(upload): _*)
    }

    result match {
      case Some(r) => r
      case None => NotFound
    }
  }

  protected def massUploadFileIteratee(userEmail: String, request: RequestHeader, guid: UUID, lastModifiedDate: String): Iteratee[Array[Byte], Either[Result, GroupedFileUpload]]

  val storage: Storage

  trait Storage {
    def findCurrentFileGroup(userEmail: String): Option[FileGroup]
    def findGroupedFileUpload(fileGroupId: Long, guid: UUID): Option[GroupedFileUpload]
  }

  private def authorizedUploadBodyParser(guid: UUID, lastModifiedDate: String) =
    AuthorizedBodyParser(anyUser) { user => uploadBodyParser(user.email, guid, lastModifiedDate) }

  private def uploadBodyParser(userEmail: String, guid: UUID, lastModifiedDate: String) =
    BodyParser("Mass upload bodyparser") { request =>
      massUploadFileIteratee(userEmail, request, guid, lastModifiedDate)
    }

  private def isUploadComplete(upload: GroupedFileUpload): Boolean =
    (upload.uploadedSize == upload.size) && (upload.size > 0)

  private def showRequestHeaders(upload: GroupedFileUpload): Seq[(String, String)] = Seq(
    (CONTENT_LENGTH, s"${upload.uploadedSize}"),
    (CONTENT_RANGE, s"0-${upload.uploadedSize - 1}/${upload.size}"),
    (CONTENT_DISPOSITION, s"attachment ; filename=${upload.name}"))
}

object MassUploadController extends MassUploadController {

  override protected def massUploadFileIteratee(userEmail: String, request: RequestHeader, guid: UUID, lastModifiedDate: String): Iteratee[Array[Byte], Either[Result, GroupedFileUpload]] =
    MassUploadFileIteratee(userEmail, request, guid, lastModifiedDate)

  val storage = new DatabaseStorage

  class DatabaseStorage extends Storage {
    import org.overviewproject.tree.orm.FileJobState.InProgress

    override def findCurrentFileGroup(userEmail: String): Option[FileGroup] =
      FileGroupFinder.byUserAndState(userEmail, InProgress).headOption
      
    override def findGroupedFileUpload(fileGroupId: Long, guid: UUID): Option[GroupedFileUpload] = 
      GroupedFileUploadFinder.byFileGroupAndGuid(fileGroupId, guid).headOption
  }
}



