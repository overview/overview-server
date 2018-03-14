package com.overviewdocs.ingest.convert

import akka.actor.{Actor,ActorRef,ActorRefFactory,Props}
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.{ContentTypes,HttpEntity,HttpResponse,Multipart,ResponseEntity,RequestEntity,StatusCodes}
import akka.http.scaladsl.server.{RequestContext,Route,RouteResult}
import akka.pattern.ask
import akka.stream.{ActorMaterializer,FlowShape,OverflowStrategy,Materializer}
import akka.stream.scaladsl.{Flow,GraphDSL,Keep,MergePreferred,Sink,Source}
import akka.util.ByteString
import java.nio.charset.StandardCharsets.UTF_8
import java.util.UUID
import play.api.libs.json.{JsObject,JsNumber,JsPath,JsValue,Json,Reads}
import play.api.libs.functional.syntax._
import scala.concurrent.duration.{Duration,FiniteDuration}
import scala.concurrent.{ExecutionContext,Future}
import scala.util.{Try,Success,Failure}

import com.overviewdocs.ingest.pipeline.StepOutputFragment
import com.overviewdocs.models.File2

class HttpTaskHandler(
  stepId: String,
  maxNWorkers: Int,
  workerIdleTimeout: FiniteDuration,
  httpCreateIdleTimeout: FiniteDuration
) {
  implicit val timeout = akka.util.Timeout(Duration(2, "s")) // for ask()

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

  /** A Sink for tasks provided by the user.
    */
  def taskSink(implicit mat: Materializer): Sink[Task, Route] = {
    implicit val ec: ExecutionContext = mat.executionContext
    val actorRefFactory: ActorRefFactory = mat match {
      case am: ActorMaterializer => am.system
      case _ => throw new RuntimeException("Only ActorMaterializer can be used with this Sink")
    }

    val (timeoutActorRef, timeoutTaskSource) = Source.actorRef(maxNTimedOutTasks, OverflowStrategy.fail).preMaterialize
    val workerTaskPoolRef = actorRefFactory.actorOf(WorkerTaskPool.props(maxNWorkers, workerIdleTimeout, timeoutActorRef))

    // taskProviderRef: responds to Asks with an Option[Task]
    val taskProviderRef = actorRefFactory.actorOf(TaskProvider.props(httpCreateIdleTimeout))

    def shutdown = {
      actorRefFactory.stop(timeoutActorRef)
      actorRefFactory.stop(workerTaskPoolRef)
    }

    // With GraphDSL, build a Flow that incorporates tasks from timeoutTaskSource.
    Flow.fromGraph[Task, Task, akka.NotUsed](GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val normal = builder.add(Flow.apply[Task])
      val merge = builder.add(MergePreferred[Task](1, eagerComplete=true))
      val timedOut = builder.add(timeoutTaskSource)

      normal   ~> merge
      timedOut ~> merge.preferred

      new FlowShape(normal.in, merge.out)
    })
      .mapConcat { task =>
        // When a worker asks for a task that has been canceled, hardwire a
        // "Canceled" fragment over `task.sink` and destroy the task.
        //
        // This is better than processing cancellation messages asynchronously,
        // since it doesn't leak any closures. It means we only process a
        // cancellation when there's downstream demand from a worker -- which
        // kinda makes sense.
        if (task.isCanceled) {
          Source.single(StepOutputFragment.Canceled).runWith(task.sink)
          Vector()
        } else {
          Vector(task)
        }
      }
      .watchTermination() { (_, futureDone) =>
        // Shut down when there's no more input.
        //
        // TODO reconsider the lifecycle. Right now we only allow a single
        // Sink because we want to shut things down in an orderly fashion.
        futureDone.onComplete { case _ => shutdown }; akka.NotUsed
      }
      .mapMaterializedValue(_ => new StepRouteFactory(stepId, taskProviderRef, workerTaskPoolRef, actorRefFactory).route)
      .to(Sink.actorRefWithAck[Task](
        taskProviderRef,
        TaskProvider.Init,
        TaskProvider.Ack,
        TaskProvider.Complete
      ))
  }

  class StepRouteFactory(
    stepId: String,
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

      ask(taskProviderRef, TaskProvider.Ask).mapTo[Option[Task]].flatMap(_ match {
        case None => {
          ctx.complete(StatusCodes.NotFound)
        }
        case Some(task) => {
          // At this point, the task is in neither the Source[Task, _] nor the
          // WorkerTaskPool. Place it in the WorkerTaskPool, before we lose it.
          ask(workerTaskPoolRef, WorkerTaskPool.Create(task)).mapTo[UUID].transformWith {
            case Success(uuid) => {
              ctx.complete(HttpResponse(StatusCodes.Created, Nil, taskEntity(uuid, task)))
            }
            case Failure(ex: Exception) => {
              Source.single(StepOutputFragment.StepError(ex))
                .runWith(task.sink)
              ctx.complete(HttpResponse(StatusCodes.InternalServerError, Nil, errorEntity(ex)))
            }
            case Failure(ex: Throwable) => ???
          }
        }
      })
    }

    private def drainEntity(entity: HttpEntity)(implicit mat: Materializer): Future[akka.Done] = {
      entity.discardBytes(mat).future
    }

    /** Fetches the Task and its WorkerTask actor.
      *
      * If the Task cannot be found, responds with 404 and never calls `fn()`.
      *
      * If the Task is found to have been canceled, emits a `Canceled` fragment
      * to its Sink, responds with 404 and never calls `fn()`.
      */
    private def withTask(uuid: UUID, ctx: RequestContext)(drain: () => Future[akka.Done])(fn: (Task, ActorRef) => Future[RouteResult]): Future[RouteResult] = {
      implicit val mat = ctx.materializer
      implicit val ec = ctx.executionContext

      ask(workerTaskPoolRef, WorkerTaskPool.Get(uuid)).mapTo[Option[(Task,ActorRef)]].flatMap(_ match {
        case Some((task, workerTaskRef)) if task.isCanceled => {
          Source.single(StepOutputFragment.Canceled).runWith(task.sink)
          actorRefFactory.stop(workerTaskRef)
          drain().flatMap(_ => ctx.complete(StatusCodes.NotFound))
        }
        case None => {
          drain().flatMap(_ => ctx.complete(StatusCodes.NotFound))
        }
        case Some((task, workerTaskRef)) => {
          fn(task, workerTaskRef)
        }
      })
    }

    private def handleGet(uuid: UUID)(ctx: RequestContext): Future[RouteResult] = {
      implicit val mat = ctx.materializer
      implicit val ec = ctx.executionContext

      drainEntity(ctx.request.entity).flatMap { _ =>
        withTask(uuid, ctx)(() => Future.successful(akka.Done)) { (task, _) =>
          ctx.complete(HttpResponse(entity=taskEntity(uuid, task)))
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

    private def taskEntity(uuid: UUID, task: Task) = jsonEntity(Json.obj(
      "id" -> uuid.toString,
      "foo" -> "bar"
    ))

    private def errorEntity(ex: Throwable) = jsonEntity(Json.obj(
      "error" -> Json.obj(
        "message" -> ex.getMessage
      )
    ))

    private def jsonEntity(jsValue: JsValue): ResponseEntity = {
      HttpEntity.Strict(ContentTypes.`application/json`, ByteString(Json.toBytes(jsValue)))
    }

    implicit val file2MetadataReads = Reads.JsObjectReads.map(File2.Metadata.apply _)
    implicit val pipelineOptionsReads = Json.reads[File2.PipelineOptions]
    implicit val stepOutputFragmentReads = Json.reads[StepOutputFragment.File2Header]
    implicit val progressChildrenReads: Reads[StepOutputFragment.ProgressChildren] = (
      (JsPath \ "children" \ "nProcessed").read[Int] and
      (JsPath \ "children" \ "nTotal").read[Int]
    )(StepOutputFragment.ProgressChildren.apply _)
    implicit val progressBytesReads: Reads[StepOutputFragment.ProgressBytes] = (
      (JsPath \ "bytes" \ "nProcessed").read[Int] and
      (JsPath \ "bytes" \ "nTotal").read[Int]
    )(StepOutputFragment.ProgressBytes.apply _)
    implicit val progressFractionReads = Reads.DoubleReads.map(StepOutputFragment.ProgressFraction.apply _)
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
      val ThumbnailPattern = """([0-9]{1,8}).(png|jpg)""".r

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
        // There's a race: how can we tell when downstream has processed this
        // fragment? Well, let's not get hung up on it: let's assume that the
        // time of downstream demand is pretty darned close to the time the
        // fragment has been processed (hence "lazily" -- otherwise, the
        // Future would complete right away).
        //
        // This is a total fail when it comes to Blob, Text and Thumbnail
        // requests, whose entities are only read after they travel down the
        // stream.
        Source.lazilyAsync(() => partToFragment(name, ctx.request.entity))
          .map { fragment =>
            if (fragment.isInstanceOf[StepOutputFragment.EndFragment]) {
              actorRefFactory.stop(workerTaskRef)
            } else {
              workerTaskRef ! WorkerTask.Heartbeat
            }
            fragment
          }
          .to(task.sink)
          .run // Future[akka.NotUsed] 
          .flatMap(_ => ctx.complete(StatusCodes.Accepted))
      }
    }

    private def handlePostMultipart(uuid: UUID, formData: Multipart.FormData)(ctx: RequestContext): Future[RouteResult] = {
      implicit val mat = ctx.materializer
      implicit val ec = ctx.executionContext

      withTask(uuid, ctx)(() => formData.parts.flatMapConcat(part => part.entity.dataBytes).runWith(Sink.ignore)) { (task, workerTaskRef) =>
        // There's a race that might affect fringe clients: watchTermination()
        // watches the _source_'s termination, not the _sink's_. That means the
        // request can be completed and we can respond to it before all bytes
        // have been fully processed.
        //
        // In practice, this shouldn't matter. Stream-based chunks like Body and
        // Text block the `.mapAsync()` step because they back-pressure until
        // their contents are consumed. So the only chunks left over are "done",
        // "progress" and other tiny ones. Assuming the worker sends "done" (the
        // common case: the server sends all messages in one big HTTP request), it
        // will never ask after this Task again anyway.
        //
        // Backpressure from downstream doesn't halt the response, but that isn't
        // bad: it'll prevent _future_ HTTP requests from completing, so workers
        // will all stall until their previous output is processed, which is what
        // we want.
        formData.parts
          .mapAsync(1)(part => partToFragment(part.name, part.entity))
          .map { fragment =>
            if (fragment.isInstanceOf[StepOutputFragment.EndFragment]) {
              actorRefFactory.stop(workerTaskRef)
            } else {
              workerTaskRef ! WorkerTask.Heartbeat
            }
            fragment
          }
          .watchTermination()(Keep.right)
          .to(task.sink)
          .run
          .flatMap(_ => ctx.complete(StatusCodes.Accepted))
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
            (pathEnd & entity(as[Multipart.FormData])) { formData =>
              handlePostMultipart(uuid, formData)
            } ~
            path(Segment) { name => handlePost(uuid, name) }
          }
        } ~
        get {
          path(JavaUUID) { uuid =>
            handleGet(uuid)
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
