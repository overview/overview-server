package controllers.backend

import akka.http.scaladsl.model.{ContentTypes,HttpEntity,HttpHeader,HttpRequest,HttpResponse,StatusCodes,Uri}
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.{HttpApp,Route,RouteResult}
import akka.http.scaladsl.settings.ServerSettings
import akka.util.ByteString
import com.typesafe.config.ConfigFactory
import java.nio.charset.StandardCharsets.UTF_8
import java.util.concurrent.atomic.AtomicReference
import play.api.libs.json.Json
import scala.collection.immutable
import scala.concurrent.{ExecutionContext,Future,Promise}
import scala.concurrent.duration.Duration

import com.overviewdocs.models.{DocumentIdSet,DocumentIdFilter,View,ViewFilter,ViewFilterSelection}
import com.overviewdocs.models.tables.{ApiTokens,DocumentSets,Views}
import test.helpers.InAppSpecification

class DbHttpViewFilterBackendSpec extends DbBackendSpecification with InAppSpecification {
  "DbHttpViewFilterBackend" should {
    "#resolve" should {
      import ViewFilterSelection.Operation
      import DbHttpViewFilterBackendSpec.AvoidSpecs2AkkaConflicts.HttpViewFilterServer
      import ViewFilterBackend.ResolveError

      trait ResolveScope extends DbBackendScope {
        // This is sort of an integration test. We create a _real_ HTTP server,
        // query it, and then see what happens.
        //
        // Be wary with Before and After. They don't execute when you expect.
        // The trick is that `val httpServer` will give the same object for
        // each different child trait.

        val viewFilter: Option[ViewFilter] = Some(ViewFilter("http://localhost:9001/10101010", Json.obj()))
        val documentSet = factory.documentSet(id=3L)
        val apiToken = factory.apiToken(documentSetId=Some(documentSet.id), token="mytoken")
        lazy val view = factory.view(documentSetId=documentSet.id, apiToken=apiToken.token, viewFilter=viewFilter)
        val backendTimeout = Duration(10, "ms")

        lazy val backend = new DbHttpViewFilterBackend(database, app.actorSystem, app.materializer, backendTimeout)

        def selection(ids: Vector[String], operation: Operation) = ViewFilterSelection(view.id, ids, operation)

        var lastRequest: Option[HttpRequest] = None

        def httpQuery: Uri.Query = {
          lastRequest must beSome
          lastRequest.get.uri.query(UTF_8)
        }

        def resolve(ids: Vector[String], operation: ViewFilterSelection.Operation, httpServer: HttpViewFilterServer = new HttpViewFilterServer): Either[ViewFilterBackend.ResolveError, DocumentIdFilter] = {
          httpServer.start
          val ret = try {
            await(backend.resolve(documentSet.id, selection(ids, operation)))
          } finally {
            httpServer.stop
          }

          lastRequest = httpServer.lastRequest

          ret
        }
      }

      "fetch a DocumentIdSet" in new ResolveScope {
        resolve(Vector("foo", "bar"), Operation.Any) must beRight(DocumentIdSet(Vector(
          (3L << 32) | 0L,
          (3L << 32) | 2L,
          (3L << 32) | 4L,
          (3L << 32) | 6L
        )))
      }

      "query with given IDs" in new ResolveScope {
        resolve(Vector("foo", "bar"), Operation.Any) must beRight
        httpQuery.get("ids") must beSome("foo,bar")
      }

      "query with operation=any" in new ResolveScope {
        resolve(Vector("foo", "bar"), Operation.Any) must beRight
        httpQuery.get("operation") must beSome("any")
      }

      "query with operation=all" in new ResolveScope {
        resolve(Vector("foo", "bar"), Operation.All) must beRight
        httpQuery.get("operation") must beSome("all")
      }

      "query with operation=none" in new ResolveScope {
        resolve(Vector("foo", "bar"), Operation.None) must beRight
        httpQuery.get("operation") must beSome("none")
      }

      "return UrlNotFound when View does not exist" in new ResolveScope {
        view // resolve lazy val
        blockingDatabase.delete(Views)
        await(backend.resolve(documentSet.id, selection(Vector(), Operation.Any))) must beLeft(ResolveError.UrlNotFound)
      }

      "return UrlNotFound when DocumentSet does not exist" in new ResolveScope {
        view // resolve lazy val
        blockingDatabase.delete(Views)
        blockingDatabase.delete(ApiTokens)
        blockingDatabase.delete(DocumentSets)
        await(backend.resolve(documentSet.id, selection(Vector(), Operation.Any))) must beLeft(ResolveError.UrlNotFound)
      }

      "return UrlNotFound when View.maybeFilterUrl is None" in new ResolveScope {
        override val viewFilter = None
        await(backend.resolve(documentSet.id, selection(Vector(), Operation.Any))) must beLeft(ResolveError.UrlNotFound)
      }

      "return UrlInvalid when View.maybeFilterUrl is malformed" in new ResolveScope {
        override val viewFilter = Some(ViewFilter("http://blah\\./meep", Json.obj()))
        await(backend.resolve(documentSet.id, selection(Vector(), Operation.Any))) must beLeft((x: ResolveError) => x must beAnInstanceOf[ResolveError.UrlInvalid])
      }

      "return UrlInvalid when View.maybeFilterUrl has wrong schema" in new ResolveScope {
        override val viewFilter = Some(ViewFilter("htt://example.org/blah", Json.obj()))
        await(backend.resolve(documentSet.id, selection(Vector(), Operation.Any))) must beLeft((x: ResolveError) => x must beAnInstanceOf[ResolveError.UrlInvalid])
      }

      "return HttpError on non-200 OK" in new ResolveScope {
        resolve(Vector(), Operation.None, new HttpViewFilterServer {
          override def handleRequest(request: HttpRequest) = {
            HttpResponse(StatusCodes.NotFound, immutable.Seq[HttpHeader](), HttpEntity(ContentTypes.`text/plain(UTF-8)`, "failure message".getBytes(UTF_8)), request.protocol)
          }
        }) must beEqualTo(Left(ResolveError.HttpError("http://localhost:9001/10101010?ids=&operation=none", "404 Not Found: failure message")))
      }

      "return HttpError even when response is invalid utf-8" in new ResolveScope {
        resolve(Vector(), Operation.None, new HttpViewFilterServer {
          override def handleRequest(request: HttpRequest) = {
            HttpResponse(StatusCodes.NotFound, immutable.Seq[HttpHeader](), HttpEntity(ContentTypes.`application/octet-stream`, Array[Byte](0xaa.toByte)), request.protocol)
          }
        }) must beLeft((x: ResolveError) => x must beAnInstanceOf[ResolveError.HttpError])
      }

      "return HttpError when connection is refused" in new ResolveScope {
        await(backend.resolve(documentSet.id, selection(Vector(), Operation.Any))) must beLeft((x: ResolveError) => x must beAnInstanceOf[ResolveError.HttpError])
      }

      "return HttpTimeout when streaming response times out" in new ResolveScope {
        override val backendTimeout = Duration(100, "microseconds") // backend should fail to connect
        resolve(Vector("stalled-response"), Operation.None, new HttpViewFilterServer {
          import akka.stream.scaladsl.{Source,SourceQueueWithComplete}

          var queue: SourceQueueWithComplete[ByteString] = _
          val chunks: Source[ByteString,_] = Source.queue(1, akka.stream.OverflowStrategy.fail)
            .mapMaterializedValue((q: SourceQueueWithComplete[ByteString]) => queue = q)

          override def handleRequest(request: HttpRequest) = {
            // A response that never finishes
            val entity = HttpEntity.Chunked.fromData(ContentTypes.`application/octet-stream`, chunks)
            HttpResponse(StatusCodes.OK, immutable.Seq[HttpHeader](), entity, request.protocol)
          }

          override def prepareForStop = {
            queue.complete
          }
        }) must beLeft(ResolveError.HttpTimeout("http://localhost:9001/10101010?ids=stalled-response&operation=none"))
      }

      "return HttpTimeout when connecting but not receiving headers" in new ResolveScope {
        override val backendTimeout = Duration(5, "ms") // backend should _start_ streaming, but not _finish_
        resolve(Vector("no-headers"), Operation.None, new HttpViewFilterServer {
          val resultPromise = Promise[RouteResult]()

          override protected def routes: Route = { ctx =>
            lastRequest = Some(ctx.request)
            resultPromise.future // will never respond until the server is stopped
          }

          override def prepareForStop = {
            resultPromise.success(RouteResult.Complete(handleRequest(lastRequest.get)))
          }
        }) must beLeft(ResolveError.HttpTimeout("http://localhost:9001/10101010?ids=no-headers&operation=none"))
      }

      "return InvalidHttpResponse when Content-Type is not application/octet-stream" in new ResolveScope {
        resolve(Vector(), Operation.None, new HttpViewFilterServer {
          override def handleRequest(request: HttpRequest) = {
            HttpResponse(StatusCodes.OK, immutable.Seq[HttpHeader](), HttpEntity(ContentTypes.`text/html(UTF-8)`, Array[Byte](0xaa.toByte)), request.protocol)
          }
        }) must beLeft(ResolveError.InvalidHttpResponse("http://localhost:9001/10101010?ids=&operation=none", "Expected Content-Type: application/octet-stream; got: text/html; charset=UTF-8"))
      }
    }
  }
}

object DbHttpViewFilterBackendSpec {
  object AvoidSpecs2AkkaConflicts {
    import akka.Done
    import akka.actor.ActorSystem
    import scala.concurrent.ExecutionContext
    import scala.util.Try

    /** A real HTTP server.
      *
      * Life-cycle is a bit funny. Externally, it looks like this:
      *
      * 1. Call `httpServer.start`; it resolves when listening on port 9001
      * 2. Call `httpServer.stop`; it resolves when connections are closed
      *
      * Internally, there's lots of fun stuff:
      *
      * * `startServer` is blocking. It completes when connections are closed.
      *   So that's what we call in httpServer.start (and we listen for
      *   completion in httpServer.stop).
      * * `postHttpBinding` is calaled when listening on port 9001. So we
      *   wait for its signal in httpServer.start.
      * * HttpApp calls `waitForShutdownSignal` internally. We signal it
      *   in httpServer.stop.
      */
    class HttpViewFilterServer extends HttpApp {
      // Crazy-weird: if we pass InAppSpecification's ActorSystem, then any
      // future invocations of start() will use this exact same class _instance_.
      // In other words: every time we're in handleRequest(), `this` will point
      // to the same in-memory address. So `this.lastRequest = ...` sets the wrong
      // thing.
      //
      // Workaround: create a new ActorSystem for each HTTP server.
      private val actorSystemConfig = ConfigFactory.parseString("""
        akka.log-dead-letters: 0
        akka.actor.provider: local
        akka.http.server.request-timeout: 200h
      """).withFallback(ConfigFactory.load)
      private val actorSystem = ActorSystem("httpSpecificActorSystem", actorSystemConfig)

      protected val shutdownPromise = Promise[Done]()
      protected val listeningPromise = Promise[Done]()
      protected var finishedShutdown: Future[Unit] = _

      final var lastRequest: Option[HttpRequest] = None

      override protected def postHttpBinding(binding: Http.ServerBinding): Unit = {
        listeningPromise.trySuccess(Done)
      }

      override protected def postServerShutdown(attempt: Try[Done], system: ActorSystem): Unit = () // do not log

      override protected def waitForShutdownSignal(system: ActorSystem)(implicit ec: ExecutionContext): Future[Done] = {
        shutdownPromise.future
      }

      protected def handleRequest(request: HttpRequest): HttpResponse = {
        lastRequest = Some(request)
        val entity = HttpEntity(ContentTypes.`application/octet-stream`, Array[Byte](0xaa.toByte)) // 0b10101010
        HttpResponse(StatusCodes.OK, immutable.Seq[HttpHeader](), entity, request.protocol)
      }

      override protected def routes: Route = { ctx =>
        Future.successful(RouteResult.Complete(handleRequest(ctx.request)))
      }

      def await[T](f: Future[T]): T = {
        scala.concurrent.Await.result(f, Duration.Inf)
      }

      def start: Unit = {
        val settings = ServerSettings(actorSystemConfig)
        finishedShutdown = Future({ startServer("localhost", 9001, settings, actorSystem) })(actorSystem.dispatcher)
        await(listeningPromise.future)
      }

      def stop: Unit = {
        prepareForStop
        shutdownPromise.trySuccess(Done)
        await(finishedShutdown)
        await(actorSystem.terminate)
      }

      def prepareForStop: Unit = ()
    }
  }
}
