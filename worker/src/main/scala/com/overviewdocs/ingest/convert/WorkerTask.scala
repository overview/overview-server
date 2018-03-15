package com.overviewdocs.ingest.convert

import akka.actor.{Actor,ActorRef,Props,Status,Timers}
import akka.http.scaladsl.model.{HttpRequest,HttpResponse}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import java.time.{Duration=>JDuration,Instant}
import scala.concurrent.duration.{Duration,FiniteDuration}
import scala.concurrent.Future
import scala.util.{Success,Failure}

import com.overviewdocs.ingest.pipeline.{StepOutputFragment,StepOutputFragmentCollector}

/** The server's understanding of the state of a (HTTP-client) worker.
  *
  * Notice how little state we store. We don't identify the HTTP client or any
  * concurrent HTTP requests. We don't even know the task ID. All we do is
  * pipe all input to the Task.
  *
  * Inputs into this Actor:
  *
  * * GetForAsker(asker) -- sends Some((task, self)) to asker.
  * * Tick -- checks lastActivityAt and task.isCanceled; can send to
  *           `timeoutActorRef` or generate a `StepOutputFragment.Canceled`.
  * * stop -- when the parent stops this Actor, the job is complete.
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
  private var processingFragments: Boolean = false
  private var fragmentCollectorState: StepOutputFragmentCollector.State = StepOutputFragmentCollector.State.Start(task.writtenFile2)

  implicit val ec = context.dispatcher
  private val tickInterval = workerIdleTimeout / 10
  timers.startPeriodicTimer("tick", WorkerTask.Tick, tickInterval)

  private var lastActivityAt = Instant.now

  private val timeoutTemporalAmount = JDuration.ofNanos(workerIdleTimeout.toNanos)
  private def isTimedOut: Boolean = {
    lastActivityAt.plus(timeoutTemporalAmount).isBefore(Instant.now)
  }

  private def updateLastActivity: Unit = {
    lastActivityAt = Instant.now
  }

  override def receive = {
    case WorkerTask.Tick => {
      if (isTimedOut) {
        timeoutActorRef ! task
        context.stop(self)
      }
    }

    case WorkerTask.GetForAsker(asker) => {
      updateLastActivity
      asker ! Some((task, self))
    }

    case WorkerTask.ProcessFragments(fragments) => {
      updateLastActivity
      val asker = sender
      implicit val mat = ActorMaterializer.create(context)

      if (processingFragments) {
        sender ! Status.Failure(new RuntimeException("Overview is already processing a POST to this task"))
      } else {
        processingFragments = true
        fragments
          .scanAsync(fragmentCollectorState)(task.stepOutputFragmentCollector.transitionState)
          .mapConcat { state =>
            self ! WorkerTask.UpdateState(state)
            state.toEmit
          }
          // Any ConvertOutputElement at this point is "safe" to read -- it
          // does not depend on upstream HTTP POST request bytes like a
          // StepOutputFragment does. So once we've seen the final fragment in
          // the POST, we've read all the bytes from it
          .watchTermination() { (mat, doneFuture) =>
            doneFuture.onComplete {
              case Success(_) => self ! WorkerTask.DoneProcessingFragments(mat, asker)
              case Failure(err) => asker ! Status.Failure(err)
            }
          }
          .runWith(task.sink)
      }
    }

    case WorkerTask.UpdateState(state) => {
      updateLastActivity
      fragmentCollectorState = state
    }

    case WorkerTask.DoneProcessingFragments(materializedValue, asker) => {
      processingFragments = false
      asker ! Status.Success(materializedValue)
      updateLastActivity
      if (fragmentCollectorState.isInstanceOf[StepOutputFragmentCollector.State.End]) {
        context.stop(self)
      }
    }
  }
}

object WorkerTask {
  private case object Tick
  case class GetForAsker(asker: ActorRef)
  case class ProcessFragments(fragments: Source[StepOutputFragment, Any])
  private case class UpdateState(state: StepOutputFragmentCollector.State)
  private case class DoneProcessingFragments(materializedValue: Any, asker: ActorRef)

  def props(
    task: Task,
    workerIdleTimeout: FiniteDuration,
    timeoutActorRef: ActorRef
  ) = Props(classOf[WorkerTask], task, workerIdleTimeout, timeoutActorRef)
}
