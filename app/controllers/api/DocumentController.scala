package controllers.api

import java.util.UUID
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.{JsArray,JsNull,JsNumber,JsObject,JsString,JsValue,Json}
import play.api.mvc.{RequestHeader,Result}
import scala.concurrent.Future

import controllers.auth.ApiAuthorizedAction
import controllers.auth.Authorities.userOwningDocumentSet
import controllers.backend.{DocumentBackend,SelectionBackend}
import models.pagination.PageRequest
import models.{Selection,SelectionRequest}
import org.overviewproject.models.DocumentHeader

trait DocumentController extends ApiController with ApiSelectionHelpers {
  import DocumentController.Field
  protected val documentBackend: DocumentBackend

  private def _indexDocuments(selection: Selection, pageRequest: PageRequest, fields: Set[Field]): Future[Result] = {
    for { documents <- documentBackend.index(selection, pageRequest, Field.needFullDocuments(fields)) }
    yield {
      val jsObjects = documents.items.map(d => Field.formatDocument(d, fields))
      val json = JsObject(Seq(
        "selectionId" -> JsString(selection.id.toString),
        "pagination" -> views.json.api.pagination.PageInfo.show(documents.pageInfo),
        "items" -> JsArray(jsObjects)
      ))
      Ok(json)
    }
  }

  private def _streamDocumentsInner(selection: Selection, pageRequest: PageRequest, fields: Set[Field], documentCount: Int): Result = {
    import play.api.libs.iteratee._

    val start = pageRequest.offset
    val end = scala.math.min(start + pageRequest.limit, documentCount)
    val batchSize = DocumentController.StreamingPageLimit

    def fetchPage(pageStart: Int): Future[String] = {
      import play.api.libs.iteratee.Execution.defaultExecutionContext
      documentBackend.index(selection, PageRequest(pageStart, batchSize), Field.needFullDocuments(fields))
        .map { (documents) =>
          val initialComma = if (pageStart != start && documents.items.nonEmpty) "," else ""
          val jsObjects = documents.items.map(d => Field.formatDocument(d, fields))
          s"${initialComma}${jsObjects.map(_.toString).mkString(",")}"
        }
    }

    val jsObjectChunks = Enumerator.unfoldM[Int,String](start) { (pageStart) =>
      if (pageStart < end) {
        fetchPage(pageStart).map(Some(pageStart + batchSize, _))
      } else {
        Future.successful(None)
      }
    }

    val content = Enumerator(s"""{"selectionId":"${selection.id.toString}","pagination":{"offset":${pageRequest.offset},"limit":${pageRequest.limit},"total":${documentCount}},"items":[""")
      .andThen(jsObjectChunks)
      .andThen(Enumerator("]}"))

    Ok.chunked(content)
      .as("application/json")
  }

  private def _streamDocuments(selection: Selection, pageRequest: PageRequest, fields: Set[Field]): Future[Result] = {
    for {
      documentCount <- selection.getDocumentCount
    } yield _streamDocumentsInner(selection, pageRequest, fields, documentCount)
  }

  private def _indexIds(selection: Selection): Future[Result] = {
    for { ids <- selection.getAllDocumentIds }
    yield Ok(JsArray(ids.map(JsNumber(_))))
  }

  def index(documentSetId: Long, fields: String) = ApiAuthorizedAction(userOwningDocumentSet(documentSetId)).async { request =>
    requestToSelection(documentSetId, request).flatMap(_ match {
      case Left(result) => Future.successful(result)
      case Right(selection) => {
        val fieldSet = Set(Field.id) ++ Field.parseManyOrDefaults(fields)

        if (fieldSet == Set(Field.id)) {
          _indexIds(selection)
        } else {
          val streaming = RequestData(request).getBoolean("stream").getOrElse(false)

          val pageLimit = if (streaming) {
            Int.MaxValue
          } else if (Field.needFullDocuments(fieldSet)) {
            DocumentController.MaxTextPageLimit
          } else {
            DocumentController.MaxPageLimit
          }
          val pr = pageRequest(request, pageLimit)

          if (streaming) {
            _streamDocuments(selection, pr, fieldSet)
          } else {
            _indexDocuments(selection, pr, fieldSet)
          }
        }
      }
    })
  }

  def show(documentSetId: Long, documentId: Long) = ApiAuthorizedAction(userOwningDocumentSet(documentSetId)).async {
    documentBackend.show(documentSetId, documentId).map(_ match {
      case Some(document) => Ok(Field.formatDocument(document, Field.all))
      case None => NotFound(jsonError("not-found", s"Document $documentId not found in document set $documentSetId"))
    })
  }
}

object DocumentController extends DocumentController {
  override protected val documentBackend = DocumentBackend

  private val MaxPageLimit = 1000
  private val MaxTextPageLimit = 20
  private val StreamingPageLimit = 20

  sealed trait Field
  object Field {
    case object id extends Field
    case object documentSetId extends Field
    case object keywords extends Field
    case object metadata extends Field
    case object pageNumber extends Field
    case object suppliedId extends Field
    case object text extends Field
    case object title extends Field
    case object url extends Field

    val defaults: Set[Field] = Set(id, keywords, pageNumber, suppliedId, title, url)
    val all: Set[Field] = Set(id, documentSetId, keywords, metadata, pageNumber, suppliedId, text, title, url)
    val fullDocumentKeywords: Set[Field] = Set(text, metadata)

    def needFullDocuments(fields: Set[Field]): Boolean = fields.intersect(fullDocumentKeywords).nonEmpty

    def name(field: Field): String = field match {
      case `id` => "id"
      case `documentSetId` => "documentSetId"
      case `keywords` => "keywords"
      case `metadata` => "metadata"
      case `pageNumber` => "pageNumber"
      case `suppliedId` => "suppliedId"
      case `text` => "text"
      case `title` => "title"
      case `url` => "url"
    }

    def format(field: Field, document: DocumentHeader): JsValue = field match {
      case `id` => JsNumber(document.id)
      case `documentSetId` => JsNumber(document.documentSetId)
      case `keywords` => JsArray(document.keywords.map(JsString.apply))
      case `metadata` => document.metadataJson
      case `pageNumber` => document.pageNumber.map(JsNumber(_)).getOrElse(JsNull)
      case `suppliedId` => JsString(document.suppliedId)
      case `text` => JsString(document.text)
      case `title` => JsString(document.title)
      case `url` => document.url.map(JsString).getOrElse(JsNull)
    }

    def formatDocument(document: DocumentHeader, fields: Set[Field]): JsObject = {
      JsObject(fields.toSeq.map(f => name(f) -> format(f, document)))
    }

    def parseOne(string: String): Option[Field] = string match {
      case "id" => Some(id)
      case "documentSetId" => Some(documentSetId)
      case "keywords" => Some(keywords)
      case "metadata" => Some(metadata)
      case "pageNumber" => Some(pageNumber)
      case "suppliedId" => Some(suppliedId)
      case "text" => Some(text)
      case "title" => Some(title)
      case "url" => Some(url)
      case _ => None
    }

    /** Parses a comma-separated list of Fields. */
    def parseMany(string: String): Set[Field] = {
      string
        .split(",")
        .map(parseOne)
        .flatten
        .toSet
    }

    /** Parses a comma-separated list of Fields. Returns defaults if empty. */
    def parseManyOrDefaults(string: String): Set[Field] = {
      val parsed = parseMany(string)
      if (parsed.nonEmpty) {
        parsed
      } else {
        defaults
      }
    }
  }
}
