package com.overviewdocs.ingest.convert

import akka.actor.{Actor,ActorContext,ActorRef,Props}
import akka.http.scaladsl.model.{RequestEntity,StatusCodes}
import akka.http.scaladsl.server.{RequestContext,RouteResult}
import akka.pattern.ask
import akka.stream.{ActorMaterializer,OverflowStrategy,SourceShape}
import akka.stream.scaladsl.{GraphDSL,MergePreferred,Sink,Source}
import java.util.UUID
import play.api.libs.json.Json
import scala.concurrent.duration.{Duration,FiniteDuration}
import scala.concurrent.{ExecutionContext,Future}
import scala.util.{Success,Failure}

import com.overviewdocs.ingest.pipeline.StepOutputFragment

class HttpTaskServer(
  stepId: String,
  taskSource: Source[Task, akka.NotUsed],
  actorContext: ActorContext,
  maxNWorkers: Int,
  workerIdleTimeout: FiniteDuration
) {
  implicit val ec: ExecutionContext = actorContext.dispatcher
  implicit val mat = ActorMaterializer.create(actorContext)
  implicit val timeout = akka.util.Timeout(Duration(2, "s")) // for ask()

  // TaskProvider: respond to Asks with an Option[Task] (backed by a Source[Task])
  private class TaskProvider extends Actor {
    import context._

    def empty(sinkActor: ActorRef): Receive = {
      case task: Task => become(full(task, sinkActor))
      case TaskProvider.Complete => context.stop(self)
      case TaskProvider.Ask => sender ! None
    }

    def full(task: Task, sinkActor: ActorRef): Receive = {
      case TaskProvider.Complete => context.stop(self)
      case TaskProvider.Ask => {
        sender ! Some(task)
        sinkActor ! TaskProvider.Ack
        become(empty(sinkActor))
      }
    }

    override def receive = {
      case TaskProvider.Init => {
        become(empty(sender))
        sender ! TaskProvider.Ack
      }
      case TaskProvider.Complete => context.stop(self)
    }
  }
  private object TaskProvider {
    case object Init
    case object Ack
    case object Complete
    case object Ask
  }

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
  private val (timeoutActorRef, timeoutTaskSource) = Source.actorRef(maxNTimedOutTasks, OverflowStrategy.fail).preMaterialize
  private val workerTaskPoolRef = actorContext.actorOf(WorkerTaskPool.props(maxNWorkers, workerIdleTimeout, timeoutActorRef))

  private def shutdown = {
    actorContext.stop(timeoutActorRef)
    actorContext.stop(workerTaskPoolRef)
  }

  private val safeTaskSource = taskSource
    .watchTermination() { (_, futureDone) => futureDone.onComplete { case _ => shutdown }; akka.NotUsed }

  /** A Source of all incomplete Tasks.
    *
    * WorkerTask actors will repopulate this Source should any workers
    * time out.
    */
  private val retryingTaskSource: Source[Task, akka.NotUsed] = {
    Source.fromGraph(GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val merge = builder.add(MergePreferred[Task](1))
      val normal = builder.add(safeTaskSource)
      val timedOut = builder.add(timeoutTaskSource)

      normal   ~> merge
      timedOut ~> merge.preferred

      new SourceShape(merge.out)
    })
  }

  // taskProviderRef: responds to Asks with an Option[Task]
  private val taskProviderRef = actorContext.actorOf(Props[TaskProvider]())
  retryingTaskSource
    .runWith(Sink.actorRefWithAck[Task](
      taskProviderRef,
      TaskProvider.Init,
      TaskProvider.Ack,
      TaskProvider.Complete
    ))

  val route = {
    import akka.http.scaladsl.server.Directives._

    pathPrefix(stepId) {
      post {
        pathEnd {
          handleCreate
        } ~
        path(JavaUUID) { uuid =>
          handlePost(uuid)
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

  def handleCreate(ctx: RequestContext): Future[RouteResult] = {
    ask(taskProviderRef, TaskProvider.Ask).mapTo[Option[Task]].flatMap(_ match {
      case None => ctx.complete(StatusCodes.NotFound)
      case Some(task) => {
        ask(workerTaskPoolRef, WorkerTaskPool.Create(task)).mapTo[UUID].transformWith {
          case Success(uuid) => ctx.complete((StatusCodes.Created, taskEntity(uuid, task)))
          case Failure(ex: Exception) => {
            Source.single(StepOutputFragment.StepError(ex))
              .runWith(task.sink)
            ctx.complete((StatusCodes.ServerError, errorEntity(ex)))
          }
        }
      }
    })
  }

  def handleGet(uuid: UUID)(ctx: RequestContext): Future[RouteResult] = {
    ask(workerTaskPoolRef, WorkerTaskPool.Get(uuid)).mapTo[Option[(Task,ActorRef)]].flatMap(_ match {
      case None => ctx.complete(StatusCodes.NotFound)
      case Some((task, _)) => ctx.complete(taskEntity(uuid, task))
    })
  }

  def handleHead(uuid: UUID)(ctx: RequestContext): Future[RouteResult] = {
    ask(workerTaskPoolRef, WorkerTaskPool.Get(uuid)).mapTo[Option[(Task,ActorRef)]].flatMap(_ match {
      case None => ctx.complete(StatusCodes.NotFound)
      case Some((task, _)) => ctx.complete(StatusCodes.OK)
    })
  }

  def handlePost(uuid: UUID)(ctx: RequestContext): Future[RouteResult] = {
    ask(workerTaskPoolRef, WorkerTaskPool.Get(uuid)).mapTo[Option[(Task,ActorRef)]].flatMap(_ match {
      case None => ctx.complete(StatusCodes.NotFound) // consumes the content
      case Some((task, workerTaskRef)) => {
        val ok = pipeRequestEntityToTaskSink(ctx.request.entity, task.sink, workerTaskRef)
          .map(_ => StatusCodes.Accepted)
        ctx.complete(ok)
      }
    })
  }

  private def taskEntity(uuid: UUID, task: Task) = Json.obj(
    "id" -> uuid.toString,
    "foo" -> "bar"
  )

  private def errorEntity(ex: Exception) = Json.obj(
    "error" -> Json.obj(
      "message" -> ex.getMessage
    )
  )

  private def pipeRequestEntityToTaskSink(
    entity: RequestEntity,
    taskSink: Sink[StepOutputFragment, akka.NotUsed],
    workerTaskRef: ActorRef
  ): Future[akka.Done] = ???
}
