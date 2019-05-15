package controllers

import akka.stream.scaladsl.{FileIO,Source}
import akka.util.ByteString
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.{File => JFile} 
import javax.inject.Inject
import play.api.Configuration
import play.api.http.HttpEntity
import play.api.i18n.MessagesApi
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.libs.json.{JsObject,JsString,Json,Reads,Writes}
import play.api.mvc.ResponseHeader
import play.api.mvc.Result
import play.api.{Play,Logger}
import scala.concurrent.{Future,blocking}

import com.overviewdocs.blobstorage.BlobStorage
import com.overviewdocs.models.{BlobStorageRef,Document,File,Page,PdfNote,PdfNoteCollection}
import com.overviewdocs.util.ContentDisposition
import controllers.auth.Authorities.{anyUser,userOwningDocument,userOwningDocumentSet,userViewingDocumentSet}
import controllers.auth.AuthorizedAction
import controllers.backend.{DocumentBackend,FileBackend,File2Backend,PageBackend}

class DocumentController @Inject() (
  documentBackend: DocumentBackend,
  blobStorage: BlobStorage,
  fileBackend: FileBackend,
  file2Backend: File2Backend,
  pageBackend: PageBackend,
  val controllerComponents: ControllerComponents,
  configuration: Configuration,
  documentShowHtml: views.html.Document.show
) extends BaseController {
  def showText(documentId: Long) = authorizedAction(userOwningDocument(documentId)).async { implicit request =>
    documentBackend.show(documentId).map(_ match {
      case Some(document) => {
        val extraHeaders: Seq[(String,String)] = if (document.isFromOcr) Seq("Generated-By" -> "tesseract") else Seq()
        Ok(document.text).withHeaders(extraHeaders: _*)
      }
      case None => NotFound
    })
  }

  // Take file path, prepend root storage path. Disallow unsafe paths and non pdf
  def externalFilePath(filename:String) = {
    if (filename.contains("..") || filename.contains("~") || (filename.indexOf(".pdf")!=filename.length-4)) {
      None
    } else {
      Some(configuration.get[String]("blobStorage.file.baseDirectory") + "/user/" + filename)
    }
  }

  // Handles file:// url by reading from local filesystem
  // We clean the filename first for security -- can only serve from specified directory
  def showFile(filename: String) = authorizedAction(anyUser).async { implicit request =>
    Logger.info("Retrieving document file: " + filename)
    externalFilePath(filename) match {
      case Some(path) => {
        try {
          val f = new JFile(path)
          val length = blocking { f.length }
          val body = FileIO.fromPath(f.toPath)

          Future.successful(
            // Play's sendEntity() ought to allow setting a content-disposition.
            // Alas, 2.6.2 doesn't.
            //
            // Luckily, we've written our own content-disposition
            // encoder/decoder to address this.
            Ok.sendEntity(HttpEntity.Streamed(body, Some(length), Some("application/pdf")))
              .withHeaders(CONTENT_DISPOSITION -> ContentDisposition.fromFilename(filename).contentDisposition.replaceFirst("attachment", "inline"))
          )

        } catch {
          case ex: Exception => Future.successful(NotFound)
        }
      }
      case None => Future.successful(NotFound)
    }
  }

  // Handles /documentit/id.pdf url by reading from blob storage
  def showPdf(documentId: Long) = authorizedAction(userOwningDocument(documentId)).async { implicit request =>
    documentBackend.show(documentId).flatMap(_ match {
      case None => Future.successful(NotFound)
      case Some(document) => {
        val filename = document.title
        documentToBodyAndLength(document).map({ (body: Source[ByteString, _], length: Long) =>
          // Play's sendEntity() ought to allow setting a content-disposition.
          // Alas, 2.6.2 doesn't.
          //
          // Luckily, we've written our own content-disposition
          // encoder/decoder to address this.
          Ok.sendEntity(HttpEntity.Streamed(body, Some(length), Some("application/pdf")))
            .withHeaders(CONTENT_DISPOSITION -> ContentDisposition.fromFilename(filename).contentDisposition.replaceFirst("attachment", "inline"))
        }.tupled)
      }
    })
  }

  def show(documentId: Long) = authorizedAction(userOwningDocument(documentId)).async { implicit request =>
    documentBackend.show(documentId).map(_ match {
      case None => NotFound
      case Some(document) => Ok(documentShowHtml(document))
    })
  }

  def showJson(documentSetId: Long, documentId: Long) = authorizedAction(userViewingDocumentSet(documentSetId)).async { implicit request =>
    documentBackend.show(documentSetId, documentId).map(_ match {
      case Some(document) => Ok(views.json.Document.show(document)).withHeaders(CACHE_CONTROL -> "max-age=0")
      case None => NotFound
    })
  }

  def update(documentSetId: Long, documentId: Long) = authorizedAction(userOwningDocumentSet(documentSetId)).async { implicit request =>
    val maybeJson: Option[JsObject] = request.body.asJson.flatMap(_.asOpt[JsObject])
    val maybeMetadataJson: Option[JsObject] = maybeJson.flatMap(_.value.get("metadata")).flatMap(_.asOpt[JsObject])
    val maybeTitle: Option[String] = maybeJson.flatMap(_.value.get("title")).flatMap(_.asOpt[JsString]).map(_.value)

    implicit val pdfNoteReads = Json.reads[PdfNote]
    val pdfNoteSeqFormat = Reads.seq[PdfNote]
    val maybePdfNotes: Option[PdfNoteCollection] = maybeJson
      .flatMap(_.value.get("pdfNotes"))
      .flatMap(_.asOpt(pdfNoteSeqFormat))
      .map(notes => PdfNoteCollection(notes.toArray))

    (maybeMetadataJson, maybeTitle, maybePdfNotes) match {
      case (Some(metadataJson), None, None) => {
        documentBackend.updateMetadataJson(documentSetId, documentId, metadataJson).map(_ => NoContent)
      }
      case (None, Some(title), None) => {
        documentBackend.updateTitle(documentSetId, documentId, title).map(_ => NoContent)
      }
      case (None, None, Some(pdfNotes)) => {
        documentBackend.updatePdfNotes(documentSetId, documentId, pdfNotes).map(_ => NoContent)
      }
      case _ => {
        Future.successful(BadRequest(jsonError(
          "illegal-arguments",
          "You must pass a JSON Object with a \"metadata\" property, a \"title\" property, or a \"pdfNotes\" property which is an Array of [ { pageIndex, x, y, width, height, text } ]"
        )))
      }
    }
  }

  private def emptyBodyAndLength: Future[Tuple2[Source[ByteString, _], Long]] = {
    Future.successful((Source.empty, 0))
  }

  private def pageIdToBodyAndLength(pageId: Long): Future[Tuple2[Source[ByteString, _], Long]] = {
    pageBackend.show(pageId).flatMap(_ match {
      case Some(page) => pageToBodyAndLength(page)
      case None => emptyBodyAndLength
    })
  }

  private def pageToBodyAndLength(page: Page): Future[Tuple2[Source[ByteString, _], Long]] = {
    Future.successful((blobStorage.get(page.dataLocation), page.dataSize))
  }

  private def fileIdToBodyAndLength(fileId: Long): Future[Tuple2[Source[ByteString, _], Long]] = {
    fileBackend.show(fileId).flatMap(_ match {
      case Some(file) => fileToBodyAndLength(file)
      case None => emptyBodyAndLength
    })
  }

  private def file2IdToBodyAndLength(file2Id: Long): Future[Tuple2[Source[ByteString, _], Long]] = {
    file2Backend.lookupBlob(file2Id).map(_ match {
      case Some(BlobStorageRef(location, nBytes)) => (blobStorage.get(location), nBytes)
      case None => (Source.empty, 0L)
    })
  }

  private def fileToBodyAndLength(file: File): Future[Tuple2[Source[ByteString, _], Long]] = {
    Future.successful((blobStorage.get(file.viewLocation), file.viewSize))
  }

  private def documentToBodyAndLength(document: Document): Future[Tuple2[Source[ByteString, _], Long]] = {
    document.file2Id match {
      case Some(file2Id) => file2IdToBodyAndLength(file2Id)
      case None => document.pageId match {
        case Some(pageId) => pageIdToBodyAndLength(pageId)
        case None => {
          document.fileId match {
            case Some(fileId) => fileIdToBodyAndLength(fileId)
            case None => emptyBodyAndLength
          }
        }
      }
    }
  }
}
