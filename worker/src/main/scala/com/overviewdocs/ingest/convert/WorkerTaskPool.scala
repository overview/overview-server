package com.overviewdocs.ingest.convert

import akka.actor.{Actor,ActorRef,DeadLetter,Props,Status,Terminated}
import akka.http.scaladsl.model.{HttpRequest,HttpResponse,StatusCodes}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import java.util.UUID
import scala.concurrent.duration.FiniteDuration

/** Holds the server's references to (HTTP-client) workers and their Tasks.
  */
class WorkerTaskPool(
  /** Maximum number of workers to track. If we try to create another worker,
    * we'll see a Status.Failure.
    *
    * Normally, this could be set in the tens or hundreds -- how many workers
    * can there be? But there's a possibility of overflow if a worker misbehaves
    * and keeps registering itself over and over without ever handling the work
    * it requested. In the future, we may opt to limit ourselves to one worker
    * per IP address; for now, we don't and this number can climb.
    *
    * `Create()` askers that receive a Failure should complete the Task with a
    * `StepOutputFragment.StepError`.
    */
  val maxNWorkers: Int,

  /** Maximum amount of time a worker can remain idle on a task before we
    * RESTART it.
    *
    * Beware: if the worker idles, we RESTART it. It is the worker's
    * responsibility to complete each file. If the worker is using an iffy
    * conversion strategy -- for instance, LibreOffice -- then the _worker_
    * needs to detect the timeout itself and post a
    * `StepOutputFragment.FileError` before we time out.
    */
  val workerIdleTimeout: FiniteDuration,

  /** ActorRef where `Task`s will be sent when they time out. */
  val timeoutActorRef: ActorRef
) extends Actor {
  private implicit val ec = context.dispatcher
  private implicit val mat = ActorMaterializer.create(context)

  private var nChildren = 0

  override def receive = {
    case WorkerTaskPool.Create(id, task) => {
      if (nChildren >= maxNWorkers) {
        sender ! Status.Failure(new RuntimeException("Reached maxNWorkers"))
      } else {
        val child = context.actorOf(
          WorkerTask.props(task, workerIdleTimeout, timeoutActorRef),
          id.toString
        )
        context.watch(child)
        sender ! Status.Success(child)
        nChildren += 1
      }
    }

    case WorkerTaskPool.HandlePostRequest(id, httpRequest) => {
      context.child(id.toString) match {
        case Some(child) => child ! WorkerTask.HandlePostRequest(httpRequest, sender)
        case None => notFound(httpRequest, sender)
      }
    }

    case DeadLetter(WorkerTask.HandlePostRequest(httpRequest, sender), `self`, _) => {
      notFound(httpRequest, sender)
    }

    case WorkerTaskPool.HandleHeadRequest(id, httpRequest) => {
      context.child(id.toString) match {
        case Some(child) => child ! WorkerTask.HandleHeadRequest(httpRequest, sender)
        case None => notFound(httpRequest, sender)
      }
    }

    case DeadLetter(WorkerTask.HandleHeadRequest(httpRequest, sender), `self`, _) => {
      notFound(httpRequest, sender)
    }

    case Terminated(_) => {
      // Assume the child sent its Task back to timeoutActorRef
      nChildren -= 1
    }
  }

  private def notFound(httpRequest: HttpRequest, respondTo: ActorRef): Unit = {
    httpRequest.discardEntityBytes(mat).future
      .onComplete { case _ =>
        respondTo ! Status.Success(HttpResponse(StatusCodes.NotFound))
      }
  }

  context.system.eventStream.subscribe(self, classOf[DeadLetter])
}

object WorkerTaskPool {
  /** Creates a WorkerTask child and responds with `Status.Success(akka.Done)`
    * or `Status.Failure()`.
    *
    * `Create()` askers that receive a Failure should complete the Task with a
    * `StepOutputFragment.StepError`. Currently, the only possible failure is
    * that we overflow `.maxNWorkers`, which is either a configuration error
    * (`.maxNWorkers` is too low) or a worker error (workers aren't working on
    * the tasks they request).
    */
  case class Create(id: UUID, task: Task)

  /** Responds with a `Status.Success(HttpResponse)`.
    *
    * Internally, we just pass this to the WorkerTask. If there is no
    * WorkerTask, we generate a NotFound response.
    */
  case class HandlePostRequest(id: UUID, request: HttpRequest)

  /** Responds with a `Status.Success(HttpResponse)`.
    *
    * Internally, we just pass this to the WorkerTask. If there is no
    * WorkerTask, we generate a NotFound response.
    */
  case class HandleHeadRequest(id: UUID, request: HttpRequest)

  def props(
    maxNWorkers: Int,
    workerIdleTimeout: FiniteDuration,
    timeoutActorRef: ActorRef
  ) = Props(classOf[WorkerTaskPool], maxNWorkers, workerIdleTimeout, timeoutActorRef)
}
