package controllers.api

import akka.stream.scaladsl.{Concat,Source}
import java.util.UUID
import javax.inject.Inject
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.libs.json.{JsArray,JsBoolean,JsNull,JsNumber,JsObject,JsString,JsValue,Json}
import play.api.mvc.Result
import scala.concurrent.Future

import controllers.auth.ApiAuthorizedRequest
import controllers.auth.Authorities.userOwningDocumentSet
import controllers.backend.{DocumentBackend,DocumentSetBackend,SelectionBackend}
import models.pagination.PageRequest
import models.{Selection,SelectionRequest}
import com.overviewdocs.metadata.Metadata
import com.overviewdocs.models.{DocumentSet,DocumentHeader}

class DocumentController @Inject() (
  val documentSetBackend: DocumentSetBackend,
  val documentBackend: DocumentBackend,
  val selectionBackend: SelectionBackend,
  val controllerComponents: ApiControllerComponents
) extends ApiBaseController with ApiSelectionHelpers {
  import DocumentController.Field

  private def _indexDocuments(documentSet: DocumentSet, selection: Selection, pageRequest: PageRequest, fields: Set[Field]): Future[Result] = {
    for {
      documents <- documentBackend.index(selection, pageRequest, Field.needFullDocuments(fields))
    }
    yield {
      val jsObjects = documents.items.map(d => Field.formatDocument(documentSet, d, fields))
      val json = JsObject(Seq(
        "selectionId" -> JsString(selection.id.toString),
        "warnings" -> views.json.api.selectionWarnings(selection.warnings),
        "pagination" -> views.json.api.pagination.PageInfo.show(documents.pageInfo),
        "items" -> JsArray(jsObjects)
      ))
      Ok(json)
    }
  }

  private def _streamDocumentsInner(documentSet: DocumentSet, selection: Selection, pageRequest: PageRequest, fields: Set[Field], documentCount: Int): Result = {
    val start = pageRequest.offset
    val end = scala.math.min(start + pageRequest.limit, documentCount)
    val batchSize = DocumentController.StreamingPageLimit

    def fetchPage(pageStart: Int): Future[String] = {
      documentBackend.index(selection, PageRequest(pageStart, batchSize, false), Field.needFullDocuments(fields))
        .map { (documents) =>
          val initialComma = if (pageStart != start && documents.items.nonEmpty) "," else ""
          val jsObjects = documents.items.map(d => Field.formatDocument(documentSet, d, fields))
          s"${initialComma}${jsObjects.map(_.toString).mkString(",")}"
        }
    }

    val jsObjectChunks = Source.unfoldAsync[Int,String](start) { (pageStart) =>
      if (pageStart < end) {
        fetchPage(pageStart).map(Some(pageStart + batchSize, _))
      } else {
        Future.successful(None)
      }
    }

    val prelude = Source.single(
      s"""{"selectionId":"${selection.id.toString}","pagination":{"offset":${pageRequest.offset},"limit":${pageRequest.limit},"total":${documentCount}},"items":["""
    )
    val postlude = Source.single("]}")

    val source: Source[String, _] = prelude ++ jsObjectChunks ++ postlude

    Ok.chunked(source)
      .as("application/json")
  }

  private def _streamDocuments(documentSet: DocumentSet, selection: Selection, pageRequest: PageRequest, fields: Set[Field]): Future[Result] = {
    for {
      documentCount <- selection.getDocumentCount
    } yield _streamDocumentsInner(documentSet, selection, pageRequest, fields, documentCount)
  }

  private def _indexIds(selection: Selection): Future[Result] = {
    for { ids <- selection.getAllDocumentIds }
    yield Ok(JsArray(ids.map(JsNumber(_))))
  }

  def index(documentSetId: Long, fields: String) = apiAuthorizedAction(userOwningDocumentSet(documentSetId)).async { request =>
    documentSetBackend.show(documentSetId).flatMap(_ match {
      case None => Future.successful(NotFound)
      case Some(documentSet) => indexDocumentSet(documentSet, fields)(request)
    })
  }

  def indexDocumentSet(documentSet: DocumentSet, fields: String)(request: ApiAuthorizedRequest[_]): Future[Result] = {
    requestToSelection(documentSet.id, request).flatMap(_ match {
      case Left(result) => Future.successful(result)
      case Right(selection) => indexSelection(documentSet, selection, fields)(request)
    })
  }

  def indexSelection(documentSet: DocumentSet, selection: Selection, fields: String)(request: ApiAuthorizedRequest[_]): Future[Result] = {
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
        _streamDocuments(documentSet, selection, pr, fieldSet)
      } else {
        _indexDocuments(documentSet, selection, pr, fieldSet)
      }
    }
  }

  def show(documentSetId: Long, documentId: Long) = apiAuthorizedAction(userOwningDocumentSet(documentSetId)).async {
    for {
      maybeDocumentSet <- documentSetBackend.show(documentSetId)
      maybeDocument <- documentBackend.show(documentSetId, documentId)
    } yield (maybeDocumentSet, maybeDocument) match {
      case (Some(documentSet), Some(document)) => Ok(Field.formatDocument(documentSet, document, Field.all))
      case _ => NotFound(jsonError("not-found", s"Document $documentId not found in document set $documentSetId"))
    }
  }
}

object DocumentController {
  private val MaxPageLimit = 1000
  private val MaxTextPageLimit = 20
  private val StreamingPageLimit = 20

  sealed trait Field
  object Field {
    case object id extends Field
    case object documentSetId extends Field
    case object isFromOcr extends Field
    case object metadata extends Field
    case object pageNumber extends Field
    case object suppliedId extends Field
    case object text extends Field
    case object title extends Field
    case object tokens extends Field
    case object url extends Field

    val defaults: Set[Field] = Set(id, pageNumber, suppliedId, title, url)
    val all: Set[Field] = Set(id, documentSetId, isFromOcr, metadata, pageNumber, suppliedId, text, title, tokens, url)
    val fullDocumentKeywords: Set[Field] = Set(text, tokens, metadata)

    def needFullDocuments(fields: Set[Field]): Boolean = fields.intersect(fullDocumentKeywords).nonEmpty

    def name(field: Field): String = field match {
      case `id` => "id"
      case `documentSetId` => "documentSetId"
      case `isFromOcr` => "isFromOcr"
      case `metadata` => "metadata"
      case `pageNumber` => "pageNumber"
      case `suppliedId` => "suppliedId"
      case `text` => "text"
      case `title` => "title"
      case `tokens` => "tokens"
      case `url` => "url"
    }

    def format(field: Field, documentSet: DocumentSet, document: DocumentHeader): JsValue = field match {
      case `id` => JsNumber(document.id)
      case `documentSetId` => JsNumber(document.documentSetId)
      case `isFromOcr` => JsBoolean(document.isFromOcr)
      case `metadata` => Metadata(documentSet.metadataSchema, document.metadataJson).cleanJson
      case `pageNumber` => document.pageNumber.map(JsNumber(_)).getOrElse(JsNull)
      case `suppliedId` => JsString(document.suppliedId)
      case `text` => JsString(document.text)
      case `title` => JsString(document.title)
      case `tokens` => JsString(document.tokens.mkString(" "))
      case `url` => document.url.map(JsString).getOrElse(JsNull)
    }

    def formatDocument(documentSet: DocumentSet, document: DocumentHeader, fields: Set[Field]): JsObject = {
      JsObject(fields.toSeq.map(f => name(f) -> format(f, documentSet, document)))
    }

    def parseOne(string: String): Option[Field] = string match {
      case "id" => Some(id)
      case "documentSetId" => Some(documentSetId)
      case "isFromOcr" => Some(isFromOcr)
      case "metadata" => Some(metadata)
      case "pageNumber" => Some(pageNumber)
      case "suppliedId" => Some(suppliedId)
      case "text" => Some(text)
      case "title" => Some(title)
      case "tokens" => Some(tokens)
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
