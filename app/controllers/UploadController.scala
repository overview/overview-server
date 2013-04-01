package controllers

import java.sql.Connection
import java.util.UUID
import org.overviewproject.postgres.SquerylPostgreSqlAdapter
import org.overviewproject.tree.orm.DocumentSetCreationJobType.CsvImportJob
import org.squeryl.PrimitiveTypeMode._
import org.squeryl.Session
import play.api.Play.current
import play.api.db.DB
import play.api.libs.iteratee.Error
import play.api.libs.iteratee.Input
import play.api.libs.iteratee.Iteratee
import play.api.mvc.{ BodyParser, BodyParsers, Controller, Request, RequestHeader, Result }
import play.api.mvc.AnyContent

import org.overviewproject.postgres.LO
import org.overviewproject.tree.Ownership
import models.orm.{DocumentSet,User}
import models.orm.DocumentSetType._
import models.{OverviewDatabase,OverviewUser}
import models.upload.OverviewUpload
import controllers.auth.{AuthorizedAction,Authority,UserFactory}
import controllers.auth.Authorities.anyUser
import controllers.util.{ FileUploadIteratee, PgConnection, TransactionAction }

/**
 * Handles a file upload, storing the file in a LargeObject, updating the upload table,
 * and starting a DocumentSetCreationJob. Most of the work related to the upload happens
 * in FileUploadIteratee.
 */
trait UploadController extends Controller {

  // authorizedBodyParser doesn't belong here.
  // Should move into BaseController and/or TransactionAction, but it's not
  // clear how, since the usage here flips the dependency
  def authorizedBodyParser[A](authority: Authority)(f: OverviewUser => BodyParser[A]) = parse.using { implicit request =>
    val user : Either[Result, OverviewUser] = OverviewDatabase.inTransaction { UserFactory.loadUser(request, authority) }
    user match {
      case Left(e) => parse.error(e)
      case Right(user) => f(user)
    }
  }

  /** Handle file upload and kick of documentSetCreationJob */
  def create(guid: UUID) = TransactionAction(authorizedFileUploadBodyParser(guid)) { implicit request: Request[OverviewUpload] =>
    val upload: OverviewUpload = request.body

    val result = uploadResult(upload)
    if (result == Ok) {
      startDocumentSetCreationJob(upload)
      deleteUpload(upload)
    }

    result
  }

  private def uploadResult(upload: OverviewUpload) =
    if (upload.uploadedFile.size == 0) NotFound
    else if (upload.uploadedFile.size == upload.size) Ok
    else PartialContent

  def show(guid: UUID) = AuthorizedAction(anyUser) { implicit request =>
    def contentRange(upload: OverviewUpload): String = "0-%d/%d".format(upload.uploadedFile.size - 1, upload.size)
    def contentDisposition(upload: OverviewUpload): String = upload.uploadedFile.contentDisposition

    findUpload(request.user.id, guid).map { u =>
      uploadResult(u) match {
        case NotFound => NotFound
        case r => r.withHeaders(
          (CONTENT_RANGE, contentRange(u)),
          (CONTENT_DISPOSITION, contentDisposition(u)))
      }
    } getOrElse (NotFound)
  }

  /** Gets the guid and user info to the body parser handling the file upload */
  def authorizedFileUploadBodyParser(guid: UUID) = authorizedBodyParser(anyUser) { user => fileUploadBodyParser(user, guid) }

  def fileUploadBodyParser(user: OverviewUser, guid: UUID): BodyParser[OverviewUpload] = BodyParser("File upload") { request =>
    fileUploadIteratee(user.id, guid, request)
  }

  protected def fileUploadIteratee(userId: Long, guid: UUID, requestHeader: RequestHeader): Iteratee[Array[Byte], Either[Result, OverviewUpload]]
  protected def findUpload(userId: Long, guid: UUID): Option[OverviewUpload]
  protected def deleteUpload(upload: OverviewUpload)
  protected def startDocumentSetCreationJob(upload: OverviewUpload)
}

/**
 * UploadController implementation that uses FileUploadIteratee
 */
object UploadController extends UploadController with PgConnection {

  def fileUploadIteratee(userId: Long, guid: UUID, requestHeader: RequestHeader): Iteratee[Array[Byte], Either[Result, OverviewUpload]] =
    FileUploadIteratee.store(userId, guid, requestHeader)

  def findUpload(userId: Long, guid: UUID): Option[OverviewUpload] = OverviewUpload.find(userId, guid)

  def deleteUpload(upload: OverviewUpload) = withPgConnection { implicit c =>
    upload.delete
  }

  def startDocumentSetCreationJob(upload: OverviewUpload) {
    val documentSet = DocumentSet(
      title = upload.uploadedFile.filename,
      documentSetType = CsvImportDocumentSet,
      uploadedFileId = Some(upload.uploadedFile.id)).save

    User.findById(upload.userId).map { u => documentSet.setUserRole(u.email, Ownership.Owner) }
    documentSet.createDocumentSetCreationJob(contentsOid = Some(upload.contentsOid))
  }
}
 
