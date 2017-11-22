package controllers.backend

import akka.actor.ActorSystem
import akka.stream.{Materializer,StreamTcpException}
import com.google.inject.ImplementedBy
import java.nio.charset.CharacterCodingException
import java.nio.charset.StandardCharsets.UTF_8
import javax.inject.Inject
import scala.collection.immutable
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.util.{Success,Failure}

import com.overviewdocs.database.Database
import com.overviewdocs.models.{DocumentIdFilter,DocumentIdSet,ViewFilterSelection}
import com.overviewdocs.models.tables.Views
import com.overviewdocs.util.Logger
import _root_.util.BitSetUtil

/** Queries the database and a remote server to transform a ViewFilterSelection
  * into a DocumentIdFilter.
  */
@ImplementedBy(classOf[DbHttpViewFilterBackend])
trait ViewFilterBackend {
  def resolve(documentSetId: Long, viewFilterSelection: ViewFilterSelection): Future[Either[ViewFilterBackend.ResolveError,DocumentIdFilter]]
}

object ViewFilterBackend {
  sealed trait ResolveError
  object ResolveError {
    /** There is no ViewFilter for this (documentSetId, viewId) combination. */
    case object UrlNotFound extends ResolveError

    /** The URL is invalid.
      *
      * We don't bother translating "message" here, because the message is
      * aimed at plugin authors and sysadmins.
      */
    case class UrlInvalid(url: String, message: String) extends ResolveError

    /** The server did not respond completely in time. */
    case class HttpTimeout(url: String) extends ResolveError

    /** The server responded with an HTTP error code.
      *
      * In other words: the server is misbehaving.
      *
      * We don't bother translating "message" here, because the message is
      * aimed at plugin authors and sysadmins.
      */
    case class HttpError(url: String, message: String) extends ResolveError

    /** The server's response does not parse to a DocumentIdFilter.
      *
      * We don't bother translating "message" here, because the message is
      * aimed at plugin authors and sysadmins.
      */
    case class InvalidHttpResponse(url: String, message: String) extends ResolveError
  }
}

class DbHttpViewFilterBackend @Inject() (
  val database: Database,
  val actorSystem: ActorSystem,
  val materializer: Materializer,
  val httpTimeout: scala.concurrent.duration.FiniteDuration = Duration(10, "s")
) extends ViewFilterBackend with DbBackend {
  import database.api._
  import ViewFilterBackend.ResolveError
  import ViewFilterSelection.Operation
  import akka.http.scaladsl.Http
  import akka.stream.scaladsl.TcpIdleTimeoutException
  import akka.http.scaladsl.model.{ContentTypes,HttpRequest,HttpResponse,IllegalUriException,RequestTimeoutException,StatusCodes,Uri}
  import akka.http.scaladsl.settings.{ClientConnectionSettings,ConnectionPoolSettings}

  protected lazy val logger = Logger.forClass(getClass)

  override def resolve(documentSetId: Long, viewFilterSelection: ViewFilterSelection): Future[Either[ResolveError,DocumentIdFilter]] = {
    import scala.concurrent.ExecutionContext.Implicits.global
    database.option(filterUrlAndTokenByIdsCompiled(documentSetId, viewFilterSelection.viewId)).flatMap(_ match {
      case None => Future.successful(Left(ResolveError.UrlNotFound))
      case Some((filterUrl, apiToken)) => {
        resolveHttp(documentSetId, filterUrl, apiToken, viewFilterSelection.ids, viewFilterSelection.operation)
      }
    })
  }

  private lazy val clientConnectionSettings = {
    ClientConnectionSettings(actorSystem)
      .withConnectingTimeout(httpTimeout)
      .withIdleTimeout(httpTimeout) // if we don't set this, a server that doesn't respond will freeze the client forever
  }

  private lazy val connectionPoolSettings = {
    ConnectionPoolSettings(actorSystem)
      .withConnectionSettings(clientConnectionSettings)
  }

  private lazy val http = {
    Http(actorSystem)
  }

  private def httpResponseToResult(url: String, documentSetId: Long, httpResponse: HttpResponse): Future[Either[ResolveError,DocumentIdFilter]] = {
    import actorSystem.dispatcher

    val entity = httpResponse.entity

    // akka-http: we _must_ read the entire entity, regardless of whether
    // this is a success or a failure
    entity.toStrict(httpTimeout)(materializer).transform(strictTry => (httpResponse.status, strictTry) match {
      case (StatusCodes.OK, Success(strict)) => {
        // Success succeeded
        strict.contentType match {
          case ContentTypes.`application/octet-stream` => {
            val bitSet = BitSetUtil.bytesToBitSet(strict.data.toArray)
            val documentIdSet = DocumentIdSet(documentSetId.toInt, bitSet)
            Success(Right(documentIdSet))
          }
          case _ => {
            Success(Left(ResolveError.InvalidHttpResponse(url, "Expected Content-Type: application/octet-stream; got: " + strict.contentType.toString)))
          }
        }
      }
      case (status, Success(strict)) => {
        // We received a non-200 OK message
        val message = strict.data.utf8String
        Success(Left(ResolveError.HttpError(url, status + ": " + message)))
      }
      case (_, Failure(ex: java.util.concurrent.TimeoutException)) => {
        Success(Left(ResolveError.HttpTimeout(url)))
      }
      case (_, Failure(ex)) => {
        ex.printStackTrace()
        logger.warn("Unhandled exception", ex)
        ???
      }
    })
  }

  private def resolveHttp(documentSetId: Long, filterUrl: String, apiToken: String, ids: immutable.Seq[String], operation: Operation): Future[Either[ResolveError,DocumentIdFilter]] = {
    import actorSystem.dispatcher

    val uri = try {
      Uri.parseHttpRequestTarget(filterUrl)
    } catch {
      case ex: IllegalUriException => return Future.successful(Left(ResolveError.UrlInvalid(filterUrl, ex.getMessage)))
    }

    if (uri.scheme != "http" && uri.scheme != "https") {
      return Future.successful(Left(ResolveError.UrlInvalid(filterUrl, "expected scheme to be https or, for testing only, http")))
    }

    val fullUri = uri.withQuery(akka.http.scaladsl.model.Uri.Query(
      uri.query(UTF_8).toMap ++ Map(
        "ids" -> ids.mkString(","),
        "operation" -> (operation match {
          case Operation.Any => "any"
          case Operation.All => "all"
          case Operation.None => "none"
        })
      )
    ))
    val fullUrl = fullUri.toString

    http.singleRequest(HttpRequest(uri=fullUri), settings=connectionPoolSettings)(materializer).transformWith(_ match {
      case Success(httpResponse) => httpResponseToResult(fullUrl, documentSetId, httpResponse)
      case Failure(ex: StreamTcpException) => {
        Future.successful(Left(ResolveError.HttpError(fullUrl, ex.getMessage)))
      }
      case Failure(ex: TcpIdleTimeoutException) => {
        // akka doesn't time out if we have an HTTP server that just accepts a
        // request and doesn't respond. We force a _request_ timeout by using an
        // _idle_ timeout. Sorry -- no keepalive.
        Future.successful(Left(ResolveError.HttpTimeout(fullUrl)))
      }
      case Failure(ex) => {
        ex.printStackTrace()
        logger.warn("Unhandled exception", ex)
        ???
      }
    })
  }

  private lazy val filterUrlAndTokenByIdsCompiled = Compiled { (documentSetId: Rep[Long], viewId: Rep[Long]) =>
    Views
      .filter(_.id === viewId)
      .filter(_.documentSetId === documentSetId)
      .filter(_.maybeFilterUrl.nonEmpty)
      .map(view => (view.maybeFilterUrl.get, view.apiToken))
  }
}
