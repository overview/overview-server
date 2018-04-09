package com.overviewdocs.ingest.process

import akka.actor.{Actor,ActorRef,ActorRefFactory,Props,Status}
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.{ContentTypes,HttpEntity,HttpResponse,Multipart,ResponseEntity,RequestEntity,StatusCodes,Uri}
import akka.http.scaladsl.server.{RequestContext,Route,RouteResult}
import akka.pattern.ask
import akka.stream.{FlowShape,OverflowStrategy,Materializer}
import akka.stream.scaladsl.{Flow,GraphDSL,Keep,MergeHub,MergePreferred,Sink,Source}
import akka.util.{ByteString,Timeout}
import com.google.common.hash.HashCode
import java.nio.charset.StandardCharsets.UTF_8
import java.util.UUID
import play.api.libs.json.{JsObject,JsNumber,JsPath,JsValue,Json,Reads}
import play.api.libs.functional.syntax._
import scala.concurrent.duration.{Duration,FiniteDuration}
import scala.concurrent.{ExecutionContext,Future}
import scala.util.{Try,Success,Failure}

import com.overviewdocs.blobstorage.BlobStorage
import com.overviewdocs.ingest.File2Writer
import com.overviewdocs.ingest.model.{WrittenFile2,ConvertOutputElement}
import com.overviewdocs.models.File2
import com.overviewdocs.util.Logger

class HttpStepHandler(
  stepId: String,
  blobStorage: BlobStorage,
  stepOutputFragmentCollector: StepOutputFragmentCollector,
  maxNWorkers: Int,
  workerIdleTimeout: FiniteDuration,
  httpCreateIdleTimeout: FiniteDuration
) {
  private val logger = Logger.forClass(getClass)
  private val unresponsiveTimeout = Duration(2, "s") // for ask()
  private val postTimeout = Duration(30, "minutes")
  private val postMultipartTimeout = Duration(6, "hours")

  // The worst form of race:
  //
  // 1. maxNWorkers start tasks, all at once
  // 2. All those tasks timeout simultaneously
  // 3. During that timeout maxNWorkers all start other tasks
  // 4. All those tasks also time out
  //
  // (Theoretically, "during that timeout" could take infinite time, meaning
  // there's an infinite potential number of timed out tasks. But we assume
  // reality will be kinder.)
  private val maxNTimedOutTasks = maxNWorkers * 2

  private def retryFlow: Flow[WrittenFile2, WrittenFile2, ActorRef] = {
    val graph = GraphDSL.create(Source.actorRef(maxNTimedOutTasks, OverflowStrategy.fail).named("timeoutTaskSource")) { implicit builder => timeoutTaskSource =>
      import GraphDSL.Implicits._

      val retry = builder.add(MergePreferred[WrittenFile2](1, eagerComplete=true).named("retry"))

      timeoutTaskSource ~> retry.preferred
      new FlowShape(retry.in(0), retry.out)
    }

    Flow.fromGraph(graph)
  }

  def flow(
    actorRefFactory: ActorRefFactory
  ): Flow[WrittenFile2, ConvertOutputElement, Route] = {
    // taskProviderRef: responds to Asks with an Option[WrittenFile2]
    val taskProviderRef = actorRefFactory.actorOf(HttpTaskProvider.props(httpCreateIdleTimeout))

    // coupledFlow:
    //
    // 1. Retries, using retryFlow -- materializing to an ActorRef (timeoutActorRef)
    // 2. Emits WrittenFile2s to taskProviderRef, on demand -- this is how the
    //    Route finds tasks.
    // 3. Outputs ConvertOutputElements from a MergeHub; the materialized value
    //    is the reusable Sink[ConvertOutputElement, akka.NotUsed]
    // 4. Is "coupled": when input completes, output completes
    val coupledFlow: Flow[WrittenFile2, ConvertOutputElement, (ActorRef, Sink[ConvertOutputElement, akka.NotUsed])] = {
      val taskProviderSink = Sink.actorRefWithAck[WrittenFile2](
        taskProviderRef,
        HttpTaskProvider.Init,
        HttpTaskProvider.Ack,
        HttpTaskProvider.Complete
      ).named("taskProviderSink")

      val sink: Sink[WrittenFile2, ActorRef] = retryFlow
        .toMat(taskProviderSink)(Keep.left)

      val source = MergeHub.source[ConvertOutputElement]
      Flow.fromSinkAndSourceCoupledMat(sink, source)(Keep.both)
    }.named("HttpStepHandler.coupledFlow")

    coupledFlow
      .watchTermination() { (tuple: (ActorRef, Sink[ConvertOutputElement, akka.NotUsed]), futureDone: Future[akka.Done]) =>
        // Spin up actors
        val (timeoutActorRef, outputSink) = tuple
        val workerTaskPoolRef = actorRefFactory.actorOf(HttpWorkerPool.props(
          stepOutputFragmentCollector,
          outputSink,
          maxNWorkers,
          workerIdleTimeout,
          timeoutActorRef
        ))

        // Shut down when there's no more input.
        def shutdown = {
          timeoutActorRef ! Status.Success(akka.Done)
          actorRefFactory.stop(workerTaskPoolRef)
        }
        futureDone.onComplete {
          case Success(_) => {
            logger.info("shutdown from successful coupledFlow termination")
            shutdown
          }
          case Failure(ex) => {
            logger.info("shutdown from coupledFlow failure")
            ex.printStackTrace()
            shutdown
          }
        }(actorRefFactory.dispatcher)

        new StepRouteFactory(
          stepId,
          stepOutputFragmentCollector,
          taskProviderRef,
          workerTaskPoolRef,
          actorRefFactory
        ).route
      }
  }

  class StepRouteFactory(
    stepId: String,
    stepOutputFragmentCollector: StepOutputFragmentCollector,
    taskProviderRef: ActorRef, 
    workerTaskPoolRef: ActorRef,
    actorRefFactory: ActorRefFactory
  ) {
    import scala.language.implicitConversions
    implicit def executionContext(ctx: RequestContext): ExecutionContext = ctx.executionContext
    implicit def materializer(ctx: RequestContext): Materializer = ctx.materializer

    private def handleCreate(ctx: RequestContext): Future[RouteResult] = {
      implicit val mat = ctx.materializer
      implicit val ec = ctx.executionContext

      drainEntity(ctx.request.entity).flatMap { _ =>
        ask(taskProviderRef, HttpTaskProvider.Ask)(Timeout(httpCreateIdleTimeout + unresponsiveTimeout)).mapTo[Option[WrittenFile2]].flatMap(_ match {
          case None => {
            ctx.complete(StatusCodes.NoContent)
          }
          case Some(task) => {
            // At this point, the task is in neither the Source[WrittenFile2, _] nor the
            // HttpWorkerPool. Place it in the HttpWorkerPool, before we lose it.
            ask(workerTaskPoolRef, HttpWorkerPool.Create(task))(Timeout(unresponsiveTimeout)).mapTo[UUID].transformWith {
              case Success(uuid) => {
                taskEntity(ctx, uuid, task).flatMap(e => ctx.complete(HttpResponse(StatusCodes.Created, Nil, e)))
              }
              case Failure(ex: Exception) => {
                ex.printStackTrace()
                ctx.complete(HttpResponse(StatusCodes.InternalServerError, Nil, errorEntity(ex)))
              }
              case Failure(ex: Throwable) => ???
            }
          }
        })
      }
    }

    private def drainEntity(entity: HttpEntity)(implicit mat: Materializer): Future[akka.Done] = {
      entity.discardBytes(mat).future
    }

    /** Fetches the Task and its HttpWorkerPool actor.
      *
      * If the Task cannot be found, responds with 404 and never calls `fn()`.
      *
      * If the Task is found to have been canceled, HttpWorker will be prompted
      * to send the proper messages downstream and exit; drain() will be called
      * here.
      */
    private def withTask(uuid: UUID, ctx: RequestContext)(drain: () => Future[akka.Done])(fn: (WrittenFile2, ActorRef) => Future[RouteResult]): Future[RouteResult] = {
      implicit val mat = ctx.materializer
      implicit val ec = ctx.executionContext

      ask(workerTaskPoolRef, HttpWorkerPool.Get(uuid))(Timeout(unresponsiveTimeout))
        .mapTo[Option[(WrittenFile2,ActorRef)]]
        .recover { case ex: Exception =>
          ex.printStackTrace()
          None
        }
        .flatMap(_ match {
          case Some((task, workerTaskRef)) => {
            fn(task, workerTaskRef)
          }
          case None => {
            drain().flatMap(_ => ctx.complete(StatusCodes.NotFound))
          }
        })
    }

    private def handleGetBlob(uuid: UUID)(ctx: RequestContext): Future[RouteResult] = {
      implicit val ec = ctx.executionContext
      implicit val mat = ctx.materializer

      drainEntity(ctx.request.entity).flatMap { _ =>
        withTask(uuid, ctx)(() => Future.successful(akka.Done)) { (task, _) =>
          val byteSource = blobStorage.get(task.blob.location)
          val nBytes = task.blob.nBytes
          ctx.complete(HttpEntity.Default(ContentTypes.`application/octet-stream`, nBytes.toLong, byteSource))
        }
      }
    }

    private def handleGet(uuid: UUID)(ctx: RequestContext): Future[RouteResult] = {
      implicit val mat = ctx.materializer
      implicit val ec = ctx.executionContext

      drainEntity(ctx.request.entity).flatMap { _ =>
        withTask(uuid, ctx)(() => Future.successful(akka.Done)) { (task, _) =>
          taskEntity(ctx, uuid, task).flatMap(e => ctx.complete(HttpResponse(entity=e)))
        }
      }
    }

    private def handleHead(uuid: UUID)(ctx: RequestContext): Future[RouteResult] = {
      implicit val mat = ctx.materializer
      implicit val ec = ctx.executionContext

      drainEntity(ctx.request.entity).flatMap { _ =>
        withTask(uuid, ctx)(() => Future.successful(akka.Done)) { (task, _) =>
          ctx.complete(StatusCodes.OK)
        }
      }
    }

    private def taskEntity(ctx: RequestContext, uuid: UUID, task: WrittenFile2): Future[ResponseEntity] = {
      implicit val ec = ctx.executionContext

      for {
        externalBlobUrl <- blobStorage.getUrlOpt(task.blob.location, task.contentType)
      } yield {
        val taskUrl: String = Uri.parseAndResolve("/" + stepId + "/" + uuid, ctx.request.uri).toString
        val blobUrl: String = externalBlobUrl.getOrElse(taskUrl + "/blob")

        jsonEntity(Json.obj(
          "id" -> uuid.toString,
          "url" -> taskUrl,
          "filename" -> task.filename,
          "contentType" -> task.contentType,
          "languageCode" -> task.languageCode,
          "metadata" -> task.metadata,
          "wantOcr" -> task.wantOcr,
          "wantSplitByPage" -> task.wantSplitByPage,
          "blob" -> Json.obj(
            "url" -> blobUrl,
            "nBytes" -> task.blob.nBytes,
            "sha1" -> HashCode.fromBytes(task.blob.sha1).toString
          )
        ))
      }
    }

    private def errorEntity(ex: Throwable) = jsonEntity(Json.obj(
      "error" -> Json.obj(
        "message" -> ex.getMessage
      )
    ))

    private def jsonEntity(jsValue: JsValue): ResponseEntity = {
      HttpEntity.Strict(ContentTypes.`application/json`, ByteString(Json.toBytes(jsValue)))
    }

    implicit val file2MetadataReads: Reads[File2.Metadata] = Reads.JsObjectReads.map(File2.Metadata.apply _)
    implicit val file2HeaderReads: Reads[StepOutputFragment.File2Header] = Json.reads[StepOutputFragment.File2Header]
    implicit val progressChildrenReads: Reads[StepOutputFragment.ProgressChildren] = (
      (JsPath \ "children" \ "nProcessed").read[Int] and
      (JsPath \ "children" \ "nTotal").read[Int]
    )(StepOutputFragment.ProgressChildren.apply _)
    implicit val progressBytesReads: Reads[StepOutputFragment.ProgressBytes] = (
      (JsPath \ "bytes" \ "nProcessed").read[Int] and
      (JsPath \ "bytes" \ "nTotal").read[Int]
    )(StepOutputFragment.ProgressBytes.apply _)
    implicit val progressFractionReads: Reads[StepOutputFragment.ProgressFraction] = {
      Reads.DoubleReads.map(StepOutputFragment.ProgressFraction.apply _)
    }
    implicit val progressReads: Reads[StepOutputFragment.Progress] = {
      progressChildrenReads.map[StepOutputFragment.Progress](identity)
        .orElse(progressBytesReads.map[StepOutputFragment.Progress](identity))
        .orElse(progressFractionReads.map[StepOutputFragment.Progress](identity))
    }

    /** Turns an input into a fragment.
      *
      * If the input is invalid, the fragment is a StepError.
      */
    private def partToFragment(name: String, entity: HttpEntity)(implicit mat: Materializer): Future[StepOutputFragment] = {
      implicit val ec = mat.executionContext

      val JsonPattern = """([0-9]{1,8}).json""".r
      val BlobPattern = """([0-9]{1,8}).blob""".r
      val TextPattern = """([0-9]{1,8}).txt""".r
      val ThumbnailPattern = """([0-9]{1,8})-thumbnail.(png|jpg)""".r

      name match {
        case JsonPattern(indexInParent) => {
          entity.toStrict(workerIdleTimeout).map { e =>
            Try(Json.parse(e.data.toArray).as[JsObject])
              .flatMap { jsObject =>
                Try(jsObject.+("indexInParent" -> JsNumber(indexInParent.toInt)).as[StepOutputFragment.File2Header])
              }
              match {
                case Success(fragment) => fragment
                case Failure(ex: Exception) => StepOutputFragment.StepError(ex)
                case Failure(tr: Throwable) => ???
              }
          }
        }
        case BlobPattern(indexInParent) => {
          Future.successful(StepOutputFragment.Blob(indexInParent.toInt, entity.dataBytes))
        }
        case TextPattern(indexInParent) => {
          Future.successful(StepOutputFragment.Text(indexInParent.toInt, entity.dataBytes))
        }
        case ThumbnailPattern(indexInParent, ext) => {
          val contentType = ext match {
            case "png" => "image/png"
            case "jpg" => "image/jpeg"
            case _ => ???
          }
          Future.successful(StepOutputFragment.Thumbnail(indexInParent.toInt, contentType, entity.dataBytes))
        }
        case "progress" => {
          entity.toStrict(workerIdleTimeout).map { e =>
            Try(Json.parse(e.data.toArray).as[StepOutputFragment.Progress]) match {
              case Success(fragment) => fragment
              case Failure(ex: Exception) => StepOutputFragment.StepError(ex)
              case Failure(tr: Throwable) => ???
            }
          }
        }
        case "inherit-blob" => {
          entity.discardBytes(mat).future.map(_ => StepOutputFragment.InheritBlob)
        }
        case "done" => {
          entity.discardBytes(mat).future.map(_ => StepOutputFragment.Done)
        }
        case "error" => {
          entity.toStrict(workerIdleTimeout).map(e => StepOutputFragment.FileError(e.data.decodeString(UTF_8)))
        }
        case _ => {
          entity.discardBytes(mat).future.map { _ =>
            StepOutputFragment.StepError(new RuntimeException("Unrecognized input name: " + name))
          }
        }
      }
    }

    private def handlePost(uuid: UUID, name: String)(ctx: RequestContext): Future[RouteResult] = {
      implicit val mat = ctx.materializer
      implicit val ec = ctx.executionContext

      withTask(uuid, ctx)(() => drainEntity(ctx.request.entity)) { (task, workerTaskRef) =>
        for {
          fragment <- partToFragment(name, ctx.request.entity)
          _ <- ask(workerTaskRef, HttpWorker.ProcessFragments(Source.single(fragment)))(postTimeout)
          routeResult <- ctx.complete(StatusCodes.Accepted)
        } yield routeResult
      }
    }

    private def handlePostMultipart(uuid: UUID, formData: Multipart.FormData)(ctx: RequestContext): Future[RouteResult] = {
      implicit val mat = ctx.materializer
      implicit val ec = ctx.executionContext

      case class State(lastReadCompleted: Future[akka.Done], fragments: List[StepOutputFragment])

      withTask(uuid, ctx)(() => formData.parts.flatMapConcat(part => part.entity.dataBytes).runWith(Sink.ignore)) { (task, workerTaskRef) =>
        val fragments: Source[StepOutputFragment, akka.NotUsed] = formData.parts
          .mapAsync(1) { part => partToFragment(part.name, part.entity) }
          .mapMaterializedValue(_ => akka.NotUsed)
        for {
          _ <- ask(workerTaskRef, HttpWorker.ProcessFragments(fragments))(postMultipartTimeout)
          routeResult <- ctx.complete(StatusCodes.Accepted)
        } yield routeResult
      }
    }

    val route: Route = {
      import akka.http.scaladsl.server.Directives._

      pathPrefix(stepId) {
        post {
          pathEnd {
            handleCreate
          } ~
          pathPrefix(JavaUUID) { uuid =>
            withoutSizeLimit {
              (pathEnd & entity(as[Multipart.FormData])) { formData =>
                handlePostMultipart(uuid, formData)
              } ~
              path(Segment) { name => handlePost(uuid, name) }
            }
          }
        } ~
        get {
          pathPrefix(JavaUUID) { uuid =>
            path("blob") {
              handleGetBlob(uuid)
            } ~
            pathEnd {
              handleGet(uuid)
            }
          }
        } ~
        head {
          path(JavaUUID) { uuid =>
            handleHead(uuid)
          }
        }
      }
    }
  }
}
