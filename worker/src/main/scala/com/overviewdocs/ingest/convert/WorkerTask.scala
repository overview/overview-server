package com.overviewdocs.ingest.convert

import akka.actor.{Actor,ActorRef,Props,Status,Timers}
import akka.http.scaladsl.model.{HttpRequest,HttpResponse}
import java.time.{Duration=>JDuration,Instant}
import scala.concurrent.duration.{Duration,FiniteDuration}
import scala.concurrent.Future
import scala.util.{Success,Failure}

/** The server's understanding of the state of a (HTTP-client) worker.
  *
  * Notice how little state we store. We don't identify the HTTP client or any
  * concurrent HTTP requests. We don't even know the task ID. All we do is
  * pipe all input to the Task.
  *
  * Inputs into this Actor:
  *
  * * HandlePostRequest() -- wires up a RunnableGraph (which updates
  *                          lastActivityAt as a side-effect)
  * * HandleGetRequest() -- updates lastActivityAt
  * * Tick -- checks lastActivityAt and task.isCanceled; can send to
  *           `timeoutActorRef` or generate a `StepOutputFragment.Canceled`.
  */
class WorkerTask(
  task: Task,

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

  /** ActorRef where our `task` will be sent if we time out.
    */
  val timeoutActorRef: ActorRef
) extends Actor with Timers {
  implicit val ec = context.dispatcher
  timers.startPeriodicTimer("tick", WorkerTask.Tick, WorkerTask.TickInterval)

  private var lastActivityAt = Instant.now

  private val timeoutTemporalAmount = JDuration.ofNanos(workerIdleTimeout.toNanos)
  private def isTimedOut: Boolean = {
    lastActivityAt.plus(timeoutTemporalAmount).isBefore(Instant.now)
  }

  private def updateLastActivity: Unit = {
    lastActivityAt = Instant.now
  }

  private val taskHandler = new TaskHandler(task)

  override def receive = {
    case WorkerTask.Tick => {
      if (task.isCanceled) {
        // may someday we'll want workers to long-poll. In the meantime, all
        // we need to do is disappear and the worker can see the 404 error on
        // its next poll, signalling that it should stop what it's doing and
        // request a new task.
        context.stop(self)
      } else if (isTimedOut) {
        taskHandler.onCancel
        context.stop(self)
      }
    }

    case WorkerTask.Heartbeat => {
      updateLastActivity
    }

    case WorkerTask.HandlePostRequest(httpRequest, respondTo) => {
      updateLastActivity
      respondWithFuture(taskHandler.handlePost(httpRequest, updateLastActivity _), respondTo)
    }

    case WorkerTask.HandleHeadRequest(httpRequest, respondTo) => {
      updateLastActivity
      respondWithFuture(taskHandler.handleHead(httpRequest), respondTo)
    }

    case WorkerTask.HandleGetRequest(httpRequest, respondTo) => {
      updateLastActivity
      respondWithFuture(taskHandler.handleGet(httpRequest), respondTo)
    }
  }

  private def respondWithFuture(futureResponse: Future[HttpResponse], respondTo: ActorRef): Unit = {
    futureResponse.onComplete { case _ => updateLastActivity }
    futureResponse.onComplete {
      case Success(response) => respondTo ! Status.Success(response)
      case Failure(ex) => respondTo ! Status.Failure(ex)
    }
  }
}

object WorkerTask {
  /** Streams the POST request to the task's output -- updating lastActivityAt
    * in the process -- and sends an HttpResponse to `respondTo`.
    */
  case class HandlePostRequest(request: HttpRequest, respondTo: ActorRef)

  /** Updates lastActivityAt and sends an HttpResponse to `respondTo`.
    */
  case class HandleHeadRequest(request: HttpRequest, respondTo: ActorRef)

  /** Responds with the task's information and updates lastActivityAt.
    */
  case class HandleGetRequest(request: HttpRequest, respondTo: ActorRef)

  private case object Tick

  private case object Heartbeat

  private val TickInterval: FiniteDuration = Duration(500, "ms")

  def props(
    task: Task,
    workerIdleTimeout: FiniteDuration,
    timeoutActorRef: ActorRef
  ) = Props(classOf[WorkerTask], task, workerIdleTimeout, timeoutActorRef)
}
