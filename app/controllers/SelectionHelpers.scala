package controllers

import play.api.http.HeaderNames
import play.api.libs.concurrent.Execution.Implicits._
import play.api.i18n.Messages
import play.api.mvc.{Request,Result,Results}
import scala.concurrent.Future

import com.overviewdocs.query.{Query, QueryParser}
import controllers.auth.AuthorizedRequest
import controllers.backend.SelectionBackend
import models.{Selection,SelectionRequest}

trait SelectionHelpers extends HeaderNames with Results { self: ControllerHelpers =>
  protected val selectionBackend: SelectionBackend

  private val selectionIdKey: String = "selectionId" // query string parameter
  private val refreshKey: String = "refresh" // query string parameter

  /** Returns a Right(SelectionRequest) from the query string or form.
    *
    * Returns a Left(Result) if the query string is invalid.
    */
  protected def selectionRequest(documentSetId: Long, request: Request[_])(implicit messages: Messages): Either[Result,SelectionRequest] = {
    val reqData = RequestData(request)

    def syntaxError = {
      val message = messages("com.overviewdocs.query.SyntaxError")
      BadRequest(jsonError("illegal-arguments", message))
        .withHeaders(CONTENT_TYPE -> "application/json")
    }

    val nodeIds = reqData.getLongs("nodes")
    val tagIds = reqData.getLongs("tags")
    val tagOperation = reqData.getString("tagOperation") match {
      case Some("all") => SelectionRequest.TagOperation.All
      case Some("none") => SelectionRequest.TagOperation.None
      case _ => SelectionRequest.TagOperation.Any
    }
    val documentIds = reqData.getLongs("documents")
    val storeObjectIds = reqData.getLongs("objects")
    val maybeQOrError: Either[Result,Option[Query]] = reqData.getString("q").getOrElse("") match {
      case "" => Right(None)
      case s: String => QueryParser.parse(s) match {
        case Left(_) => Left(syntaxError)
        case Right(q) => Right(Some(q))
      }
    }

    val tagged = reqData.getString("tagged") match {
      case Some("true") => Some(true)
      case Some("false") => Some(false)
      case _ => None
    }

    maybeQOrError
      .right.map(SelectionRequest(
        documentSetId,
        nodeIds,
        tagIds,
        documentIds,
        storeObjectIds,
        tagged,
        _,
        tagOperation
      ))
  }

  /** Returns a Selection, using selectionRequest() or a selectionId parameter.
    *
    * This decides among SelectionBackend methods as follows:
    *
    * 1. If selectionId is set, use SelectionBackend.find()
    * 2. Else if query param refresh="true", use SelectionBackend.create()
    * 3. Else use SelectionBackend.findOrCreate()
    *
    * Paths 2 and 3 will always return a Right. Path 1 may return a
    * Left(NotFound), if the selection ID has expired.
    */
    protected def requestToSelection(documentSetId: Long, userEmail: String, request: Request[_])(implicit messages: Messages): Future[Either[Result,Selection]] = {
      requestToSelectionWithQuery(documentSetId, userEmail, request).map(_.right.map(_._1))
    }
 
    protected def requestToSelectionWithQuery(documentSetId: Long, userEmail: String, request: Request[_])(implicit messages: Messages): Future[Either[Result,(Selection, Option[SelectionRequest])]] = {
      val rd = RequestData(request)

      selectionRequest(documentSetId, request) match {
        case Left(error) => Future.successful(Left(error))
        case Right(sr) => {
          rd.getUUID(selectionIdKey) match {
            case Some(selectionId) =>
              selectionBackend.find(documentSetId, selectionId).map {
                case Some(selection) => Right(selection, Some(sr))
                case None => Left(NotFound(jsonError("not-found", "There is no Selection with the given selectionId. Perhaps it has expired.")))
              }
            case None =>
              val selectionFuture = rd.getBoolean(refreshKey) match {
                case Some(true) => selectionBackend.create(userEmail, sr)
                case _ => selectionBackend.findOrCreate(userEmail, sr, None)
              }
              selectionFuture.map(Right(_, Some(sr)))
          }
        }
      }
  }

  protected def requestToSelection(documentSetId: Long, request: AuthorizedRequest[_])(implicit messages: Messages): Future[Either[Result, Selection]] = {
    requestToSelection(documentSetId, request.user.email, request)
  }
}

