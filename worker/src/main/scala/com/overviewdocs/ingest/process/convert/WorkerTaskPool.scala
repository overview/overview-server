package com.overviewdocs.ingest.process.convert

import akka.actor.{Actor,ActorRef,DeadLetter,Props,Status,Terminated}
import akka.http.scaladsl.model.{HttpRequest,HttpResponse,StatusCodes}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import java.util.UUID
import scala.concurrent.duration.FiniteDuration

import com.overviewdocs.ingest.model.WrittenFile2
import com.overviewdocs.ingest.process.StepOutputFragmentCollector

/** Holds the server's references to (HTTP-client) workers and their Tasks.
  */
class WorkerTaskPool(
  val stepOutputFragmentCollector: StepOutputFragmentCollector,

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

  /** ActorRef where `WrittenFile2`s will be sent when they time out. */
  val timeoutActorRef: ActorRef
) extends Actor {
  private implicit val ec = context.dispatcher
  private implicit val mat = ActorMaterializer.create(context)

  private var nChildren = 0

  override def receive = {
    case WorkerTaskPool.Create(task) => {
      if (nChildren >= maxNWorkers) {
        sender ! Status.Failure(new RuntimeException("Reached maxNWorkers"))
      } else {
        // TODO handle task.isCanceled?
        val uuid = UUID.randomUUID
        val child = context.actorOf(
          WorkerTask.props(stepOutputFragmentCollector, task, workerIdleTimeout, timeoutActorRef),
          uuid.toString
        )
        context.watch(child)
        sender ! Status.Success(uuid)
        nChildren += 1
      }
    }

    case WorkerTaskPool.Get(uuid) => {
      context.child(uuid.toString) match {
        // TODO handle task.isCanceled?
        case Some(child) => child ! WorkerTask.GetForAsker(sender)
        case None => sender ! None
      }
    }

    case DeadLetter(WorkerTask.GetForAsker(asker), `self`, _) => {
      asker ! None
    }

    case Terminated(_) => {
      // Assume the child sent its Task back to timeoutActorRef
      nChildren -= 1
    }
  }

  context.system.eventStream.subscribe(self, classOf[DeadLetter])
}

object WorkerTaskPool {
  /** Creates a WorkerTask child and responds with
    * `Status.Success(UUID)` or `Status.Failure()`.
    *
    * `Create()` askers that receive a Failure should complete the Task with a
    * `StepOutputFragment.StepError`. Currently, the only possible failure is
    * that we overflow `.maxNWorkers`, which is either a configuration error
    * (`.maxNWorkers` is too low) or a worker error (workers aren't working on
    * the tasks they request).
    */
  case class Create(task: WrittenFile2)

  /** Responds with a `Status.Success(Option[(WrittenFile2,ActorRef)])`.
    *
    * The caller is responsible for keeping the ActorRef alive: it should send
    * periodic `WorkerTask.Heartbeat` messages during activity.
    */
  case class Get(id: UUID)

  def props(
    stepOutputFragmentCollector: StepOutputFragmentCollector,
    maxNWorkers: Int,
    workerIdleTimeout: FiniteDuration,
    timeoutActorRef: ActorRef
  ) = Props(
    classOf[WorkerTaskPool],
    stepOutputFragmentCollector,
    maxNWorkers,
    workerIdleTimeout,
    timeoutActorRef
  )
}
