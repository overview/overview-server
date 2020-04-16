package controllers

import akka.stream.scaladsl.Source
import akka.util.ByteString
import javax.inject.Inject
import play.api.http.HttpEntity
import play.api.i18n.MessagesApi
import play.api.mvc.Action
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import com.overviewdocs.blobstorage.BlobStorage
import com.overviewdocs.util.ContentDisposition
import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.{userOwningDocumentSet,userViewingDocumentSet}
import controllers.backend.{DocumentSetFileBackend,File2Backend}

class DocumentSetFileController @Inject() (
  documentSetFileBackend: DocumentSetFileBackend,
  file2Backend: File2Backend,
  blobStorage: BlobStorage,
  val controllerComponents: ControllerComponents
) extends BaseController {
  def head(documentSetId: Long, sha1: Array[Byte]) = authorizedAction(userOwningDocumentSet(documentSetId)).async {
    documentSetFileBackend.existsByIdAndSha1(documentSetId, sha1).map(_ match {
      case true => NoContent
      case false => NotFound
    })
  }

  def show(documentSetId: Long, file2Id: Long) = authorizedAction(userViewingDocumentSet(documentSetId)).async {
    documentSetFileBackend.existsForRoot(documentSetId, file2Id).flatMap(_ match {
      case false => Future.successful(NotFound)
      case true => file2Backend.lookup(file2Id).map(_ match {
        case None => NotFound
        case Some(file2) => {
          val contentDisposition = ContentDisposition.fromFilename(file2.filename).contentDisposition
          val blobRef = file2.blob.get // assume it exists -- otherwise, oops!
          val stream = blobStorage.get(blobRef.location) // assume it exists

          Ok.sendEntity(HttpEntity.Streamed(stream, Some(blobRef.nBytes), Some(file2.contentType)))
            .withHeaders(
              CONTENT_DISPOSITION -> contentDisposition
            )
        }
      })
    })
  }
}
