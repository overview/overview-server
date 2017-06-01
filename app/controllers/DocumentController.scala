package controllers

import akka.stream.scaladsl.Source
import akka.util.ByteString
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.{File => JFile} 
import javax.inject.Inject
import play.api.http.HttpEntity
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.iteratee.Enumerator
import play.api.libs.json.{JsObject,JsString,Json,Reads,Writes}
import play.api.mvc.ResponseHeader
import play.api.mvc.Result
import play.api.{Play,Logger}
import scala.concurrent.Future

import controllers.auth.Authorities.{anyUser,userOwningDocument,userOwningDocumentSet,userViewingDocumentSet}
import controllers.auth.AuthorizedAction
import controllers.backend.{DocumentBackend,FileBackend,PageBackend}
import com.overviewdocs.blobstorage.BlobStorage
import com.overviewdocs.models.{Document,File,Page,PdfNote,PdfNoteCollection}

class DocumentController @Inject() (
  documentBackend: DocumentBackend,
  blobStorage: BlobStorage,
  fileBackend: FileBackend,
  pageBackend: PageBackend
) extends Controller {

  def showText(documentId: Long) = AuthorizedAction(userOwningDocument(documentId)).async { implicit request =>
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
      Some(Play.current.configuration.getString("blobStorage.file.baseDirectory").get + "/user/" + filename)
    }
  }

  // Handles file:// url by reading from local filesystem
  // We clean the filename first for security -- can only serve from specified directory
  def showFile(filename: String) = AuthorizedAction(anyUser).async { implicit request =>
    val path = externalFilePath(filename)
    Logger.info("Retrieving document file: " + path)
    if (path != None) {

      try {
        val f = new JFile(path.get)
        val stream = Enumerator.fromFile(f)
        val length = f.length
        Logger.info("File length: " + length.toString)

        Future.successful(
          Ok.feed(stream)
            .withHeaders(
              CONTENT_TYPE -> "application/pdf",
              CONTENT_DISPOSITION -> s"""inline ; filename="$filename"""",
              CONTENT_LENGTH -> length.toString
            )
        )

      } catch {
        case ex: Exception => Future.successful(NotFound)
      }
    } else {
      Future.successful(NotFound)
    }
  }


  // Handles /documentit/id.pdf url by reading from blob storage
  def showPdf(documentId: Long) = AuthorizedAction(userOwningDocument(documentId)).async { implicit request =>
    documentBackend.show(documentId).flatMap(_ match {
      case None => Future.successful(NotFound)
      case Some(document) => {
        val filename = document.title
        documentToBodyAndLength(document).map({ (body: Source[ByteString, _], length: Long) =>
          Ok.sendEntity(HttpEntity.Streamed(body, Some(length), Some("application/pdf")))
            .withHeaders(
              CONTENT_TYPE -> "application/pdf",
              CONTENT_LENGTH -> length.toString,
              CONTENT_DISPOSITION -> s"""inline ; filename="$filename""""
            )
        }.tupled)
      }
    })
  }

  def showPng(documentId: Long) = AuthorizedAction(userOwningDocument(documentId)).async { implicit request =>
    documentBackend.show(documentId).map(_.flatMap(_.thumbnailLocation) match {
      case None => NotFound
      case Some(thumbnailLocation) => {
        val body = blobStorage.get(thumbnailLocation)
        Ok.sendEntity(HttpEntity.Streamed(body, None, Some("image/png")))
          .withHeaders(CONTENT_TYPE -> "image/png")
      }
    })
  }

  def show(documentId: Long) = AuthorizedAction(userOwningDocument(documentId)).async { implicit request =>
    documentBackend.show(documentId).map(_ match {
      case None => NotFound
      case Some(document) => Ok(views.html.Document.show(document))
    })
  }

  def showJson(documentSetId: Long, documentId: Long) = AuthorizedAction(userViewingDocumentSet(documentSetId)).async { implicit request =>
    documentBackend.show(documentSetId, documentId).map(_ match {
      case Some(document) => Ok(views.json.Document.show(document)).withHeaders(CACHE_CONTROL -> "max-age=0")
      case None => NotFound
    })
  }

  def update(documentSetId: Long, documentId: Long) = AuthorizedAction(userOwningDocumentSet(documentSetId)).async { implicit request =>
    val maybeJson: Option[JsObject] = request.body.asJson.flatMap(_.asOpt[JsObject])
    val maybeMetadataJson: Option[JsObject] = maybeJson.flatMap(_.value.get("metadata")).flatMap(_.asOpt[JsObject])
    val maybeTitle: Option[String] = maybeJson.flatMap(_.value.get("title")).flatMap(_.asOpt[JsString]).map(_.value)

    (maybeMetadataJson, maybeTitle) match {
      case (Some(metadataJson), None) => {
        documentBackend.updateMetadataJson(documentSetId, documentId, metadataJson).map(_ => NoContent)
      }
      case (None, Some(title)) => {
        documentBackend.updateTitle(documentSetId, documentId, title).map(_ => NoContent)
      }
      case _ => {
        Future.successful(BadRequest(jsonError(
          "illegal-arguments",
          "You must pass a JSON Object with a \"metadata\" property or a \"title\" property"
        )))
      }
    }
  }

  def showPdfNotes(documentId: Long) = AuthorizedAction(userOwningDocument(documentId)).async { implicit request =>
    val pdfNoteWrites = Json.writes[PdfNote]
    val pdfNoteSeqWrites = Writes.seq(pdfNoteWrites)

    documentBackend.show(documentId).map(_ match {
      case None => NotFound
      case Some(document) => {
        Ok(pdfNoteSeqWrites.writes(document.pdfNotes.pdfNotes.toSeq))
      }
    })
  }

  def updatePdfNotes(documentId: Long) = AuthorizedAction(userOwningDocument(documentId)).async { implicit request =>
    val pdfNoteReads = Json.reads[PdfNote]
    val pdfNoteSeqFormat = Reads.seq(pdfNoteReads)

    request.body.asJson.flatMap(_.asOpt(pdfNoteSeqFormat)) match {
      case None => Future.successful(BadRequest(jsonError(
        "illegal-arguments",
        "You must pass a JSON Array of {pageIndex,x,y,width,height,text} Objects"
      )))
      case Some(notes) => {
        val pdfNotes = PdfNoteCollection(notes.toArray)
        documentBackend.updatePdfNotes(documentId, pdfNotes)
          .map(_ => NoContent)
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

  private def fileToBodyAndLength(file: File): Future[Tuple2[Source[ByteString, _], Long]] = {
    Future.successful((blobStorage.get(file.viewLocation), file.viewSize))
  }

  private def documentToBodyAndLength(document: Document): Future[Tuple2[Source[ByteString, _], Long]] = {
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
}
