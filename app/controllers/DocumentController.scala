package controllers

import java.io.ByteArrayInputStream
import java.io.InputStream
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.iteratee.Enumerator
import play.api.mvc.ResponseHeader
import play.api.mvc.Result
import scala.concurrent.Future

import controllers.auth.Authorities.userOwningDocument
import controllers.auth.AuthorizedAction
import controllers.backend.{DocumentBackend,FileBackend,PageBackend}
import models.OverviewDocument
import org.overviewproject.blobstorage.BlobStorage
import org.overviewproject.models.{Document,File,Page}

trait DocumentController extends Controller {
  def showJson(documentId: Long) = AuthorizedAction(userOwningDocument(documentId)).async { implicit request =>
    documentBackend.show(documentId).map(_ match {
      case Some(document) => Ok(views.json.Document.show(OverviewDocument(document.toDeprecatedDocument)))
      case None => NotFound
    })
  }

  def showText(documentId: Long) = AuthorizedAction(userOwningDocument(documentId)).async { implicit request =>
    documentBackend.show(documentId).map(_ match {
      case Some(document) => Ok(document.text)
      case None => NotFound
    })
  }

  def showPdf(documentId: Long) = AuthorizedAction(userOwningDocument(documentId)).async { implicit request =>
    documentBackend.show(documentId).flatMap(_ match {
      case None => Future.successful(NotFound)
      case Some(document) => {
        val filename = document.title
        documentToBodyAndLength(document).map({ (body: Enumerator[Array[Byte]], length: Long) =>
          Ok
            .feed(body)
            .withHeaders(
              CONTENT_TYPE -> "application/pdf",
              CONTENT_DISPOSITION -> s"""inline ; filename="$filename"""",
              CONTENT_LENGTH -> length.toString
            )
        }.tupled)
      }
    })
  }

  def show(documentId: Long) = AuthorizedAction(userOwningDocument(documentId)).async { implicit request =>
    DocumentBackend.show(documentId).map(_ match {
      case None => NotFound
      case Some(document) => Ok(views.html.Document.show(document))
    })
  }

  private def emptyBodyAndLength: Future[Tuple2[Enumerator[Array[Byte]], Long]] = {
    Future.successful((Enumerator.empty, 0))
  }

  private def pageIdToBodyAndLength(pageId: Long): Future[Tuple2[Enumerator[Array[Byte]], Long]] = {
    pageBackend.show(pageId).flatMap(_ match {
      case Some(page) => pageToBodyAndLength(page)
      case None => emptyBodyAndLength
    })
  }

  private def pageToBodyAndLength(page: Page): Future[Tuple2[Enumerator[Array[Byte]], Long]] = {
    blobStorage.get(page.dataLocation).map((_, page.dataSize))
  }

  private def fileIdToBodyAndLength(fileId: Long): Future[Tuple2[Enumerator[Array[Byte]], Long]] = {
    fileBackend.show(fileId).flatMap(_ match {
      case Some(file) => fileToBodyAndLength(file)
      case None => emptyBodyAndLength
    })
  }

  private def fileToBodyAndLength(file: File): Future[Tuple2[Enumerator[Array[Byte]], Long]] = {
    blobStorage.get(file.viewLocation).map((_, file.viewSize))
  }

  private def documentToBodyAndLength(document: Document): Future[Tuple2[Enumerator[Array[Byte]], Long]] = {
    document.pageId match {
      case Some(pageId) => pageIdToBodyAndLength(pageId)
      case None => {
        document.fileId match {
          case Some(fileId) => fileIdToBodyAndLength(fileId)
          case None => emptyBodyAndLength
        }
      }
    }
  }

  protected val documentBackend: DocumentBackend
  protected val blobStorage: BlobStorage
  protected val fileBackend: FileBackend
  protected val pageBackend: PageBackend
}

object DocumentController extends DocumentController {
  override val blobStorage = BlobStorage
  override val documentBackend = DocumentBackend
  override val fileBackend = FileBackend
  override val pageBackend = PageBackend
}
