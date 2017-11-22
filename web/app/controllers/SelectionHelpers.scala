package controllers

import java.util.UUID
import play.api.http.HeaderNames
import scala.concurrent.ExecutionContext.Implicits.global
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
      val message = messages("com.overviewdocs.query.error.SyntaxError")
      BadRequest(jsonError("illegal-arguments", message))
        .as("application/json")
    }

    val nodeIds = reqData.getLongs("nodes")
    val tagIds = reqData.getLongs("tags")
    val tagOperation = reqData.getString("tagOperation") match {
      case Some("all") => SelectionRequest.TagOperation.All
      case Some("none") => SelectionRequest.TagOperation.None
      case _ => SelectionRequest.TagOperation.Any
    }
    val documentIds = reqData.getLongs("documents")
    val maybeDocumentIdsBitSet = reqData.getBase64BitSet("documentIdsBitSetBase64")
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

    val viewFilterSelections = Vector()

    val sortByMetadataField = reqData.getString("sortByMetadataField")

    maybeQOrError
      .right.map(SelectionRequest(
        documentSetId,
        nodeIds,
        tagIds,
        documentIds,
        maybeDocumentIdsBitSet,
        storeObjectIds,
        tagged,
        tagOperation,
        viewFilterSelections,
        _,
        sortByMetadataField
      ))
  }

  protected def parseSelectionRequestAndSelectFast(documentSetId: Long, userEmail: String, request: Request[_])(implicit messages: Messages): Future[SelectionHelpers.FastSelectionResult] = {
    selectionRequest(documentSetId, request) match {
      case Left(error) => Future.successful(SelectionHelpers.FastFailure(error))
      case Right(sr) => {
        val rd = RequestData(request)
        val maybeSelectionId = rd.getUUID(selectionIdKey)
        val refresh = rd.getBoolean(refreshKey).getOrElse(false)
        selectFast(sr, userEmail, maybeSelectionId, refresh)
      }
    }
  }

  /** Returns a response quickly, or quickly returns a SlowSelectionParameters.
    *
    * The logic goes like this:
    *
    * * If maybeSelectionId is set and maps to a cached Selection for userEmail,
    *   return FastSuccess() with the cached Selection.
    * * If maybeSelectionId is set but incorrect, return FastFailure with a 404.
    * * Otherwise, return SlowSelectionParameters.
    */
  protected def selectFast(selectionRequest: SelectionRequest, userEmail: String, maybeSelectionId: Option[UUID], refresh: Boolean)(implicit messages: Messages): Future[SelectionHelpers.FastSelectionResult] = {
    maybeSelectionId match {
      case Some(selectionId) => {
        selectionBackend.find(selectionRequest.documentSetId, selectionId).map {
          case Some(selection) => SelectionHelpers.FastSuccess(selectionRequest, selection)
          case None => SelectionHelpers.FastFailure(NotFound(jsonError("not-found", "There is no Selection with the given selectionId. Perhaps it has expired.")))
        }
      }
      case None => Future.successful(SelectionHelpers.SlowSelectionParameters(userEmail, selectionRequest, refresh))
    }
  }

  /** Returns a Selection.
    *
    * This is usually very fast, but it _may_ take seconds/minutes: if that's
    * the case, onProgres will report progress from 0.0 to 1.0 as time goes on.
    */
  protected def selectSlow(params: SelectionHelpers.SlowSelectionParameters, onProgress: Double => Unit): Future[Selection] = {
    if (params.refresh) {
      selectionBackend.create(params.userEmail, params.selectionRequest, onProgress)
    } else {
      selectionBackend.findOrCreate(params.userEmail, params.selectionRequest, None, onProgress)
    }
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
    *
    * Sorting on a non-default column can take a long time. If this call needs
    * a sort, onProgress() will be called repeatedly with numbers between 0.0
    * and 1.0 as sorting progresses.
    */
  protected def requestToSelection(documentSetId: Long, userEmail: String, request: Request[_], onProgress: Double => Unit)(implicit messages: Messages): Future[Either[Result,Selection]] = {
    parseSelectionRequestAndSelectFast(documentSetId, userEmail, request).flatMap(_ match {
      case SelectionHelpers.FastFailure(error) => Future.successful(Left(error))
      case SelectionHelpers.FastSuccess(_, selection) => Future.successful(Right(selection))
      case params: SelectionHelpers.SlowSelectionParameters => selectSlow(params, onProgress).map(s => Right(s))
    })
  }

  protected def requestToSelection(documentSetId: Long, userEmail: String, request: Request[_])(implicit messages: Messages): Future[Either[Result, Selection]] = {
    requestToSelection(documentSetId, userEmail, request, SelectionHelpers.ignoreProgress)
  }

  protected def requestToSelection(documentSetId: Long, request: AuthorizedRequest[_], onProgress: Double => Unit)(implicit messages: Messages): Future[Either[Result, Selection]] = {
    requestToSelection(documentSetId, request.user.email, request, onProgress)
  }

  protected def requestToSelection(documentSetId: Long, request: AuthorizedRequest[_])(implicit messages: Messages): Future[Either[Result, Selection]] = {
    requestToSelection(documentSetId, request.user.email, request, SelectionHelpers.ignoreProgress)
  }
}

object SelectionHelpers {
  private def ignoreProgress(d: Double): Unit = {}

  /** Response from requestToSelectionFast. */
  sealed trait FastSelectionResult
  /** The selection cannot succeed.
    *
    * Here are potential responses:
    * * 400: The request is invalid (e.g., invalid "q" parameter).
    * * 404: The request asked to look up a selection ID that isn't there.
    */
  case class FastFailure(result: Result) extends FastSelectionResult
  /** The selection was cached; here it is. */
  case class FastSuccess(selectionRequest: SelectionRequest, selection: Selection) extends FastSelectionResult
  /** The selection might take longer; call selectSlow() with this object. */
  case class SlowSelectionParameters(userEmail: String, selectionRequest: SelectionRequest, refresh: Boolean) extends FastSelectionResult
}
