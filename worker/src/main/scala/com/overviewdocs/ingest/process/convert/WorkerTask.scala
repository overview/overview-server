package com.overviewdocs.ingest.process.convert

import akka.actor.{Actor,ActorRef,Props,Status,Timers}
import akka.http.scaladsl.model.{HttpRequest,HttpResponse}
import akka.stream.{ActorMaterializer,ActorMaterializerSettings,Supervision}
import akka.stream.scaladsl.{Sink,Source}
import java.time.{Duration=>JDuration,Instant}
import scala.concurrent.duration.{Duration,FiniteDuration}
import scala.concurrent.{ExecutionContext,Future}
import scala.util.{Success,Failure}

import com.overviewdocs.ingest.File2Writer
import com.overviewdocs.ingest.model.{WrittenFile2,StepOutputFragment,ConvertOutputElement}
import com.overviewdocs.ingest.process.StepOutputFragmentCollector

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
  stepOutputFragmentCollector: StepOutputFragmentCollector,
  task: WrittenFile2,

  /** Maximum amount of time a worker can remain idle on a task before we
    * RESTART it.
    *
    * Beware: if the worker idles, we RESTART it -- meaning, we stop this Actor.
    * A timing-out worker means a BROKEN NETWORK, _not_ a failed conversion.
    * Every worker must complete every file it's given. If a worker is using an
    * iffy conversion strategy -- for instance, LibreOffice -- then the _worker_
    * needs to detect the timeout itself and post a
    * `StepOutputFragment.FileError` before _our_ timeout happens.
    */
  val workerIdleTimeout: FiniteDuration,

  /** ActorRef where our `task` will be sent if we time out.
    */
  val timeoutActorRef: ActorRef
) extends Actor with Timers {
  implicit val ec: ExecutionContext = context.dispatcher


  private val tickInterval = workerIdleTimeout / 10
  timers.startPeriodicTimer("tick", WorkerTask.Tick, tickInterval)

  private var processingFragments: Boolean = false
  private var fragmentCollectorState: StepOutputFragmentCollector.State = StepOutputFragmentCollector.State.Start(task)
  private var lastActivityAt = Instant.now

  private def isTimedOut: Boolean = {
    val timeoutTemporalAmount = JDuration.ofNanos(workerIdleTimeout.toNanos)
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

    case WorkerTask.ProcessFragments(fragments, sink) => {
      updateLastActivity
      val asker = sender
      val decider: Supervision.Decider = {
        case ex: RuntimeException => ex.printStackTrace(); Supervision.Stop
      }
      implicit val mat = ActorMaterializer(ActorMaterializerSettings(context.system).withSupervisionStrategy(decider))

      if (processingFragments) {
        sender ! Status.Failure(new RuntimeException("Overview is already processing a POST to this task"))
      } else {
        processingFragments = true
        fragments
          .scanAsync[StepOutputFragmentCollector.State](fragmentCollectorState)(stepOutputFragmentCollector.transitionState)
          .mapConcat { state =>
            // Record the state, _without_ toEmit. We're going to feed that
            // state back into scanAsync(), which emits its initial state on
            // start. If we don't clear toEmit here, we'll end up emitting
            // elements twice.
            self ! WorkerTask.UpdateState(state match {
              case s: StepOutputFragmentCollector.State.Start => s
              case StepOutputFragmentCollector.State.AtChild(p, c, _) => StepOutputFragmentCollector.State.AtChild(p, c, Nil)
              case StepOutputFragmentCollector.State.End(_) => StepOutputFragmentCollector.State.End(Nil)
            })
            state.toEmit
          }
          // Any ConvertOutputElement at this point is "safe" to read -- it
          // does not depend on upstream HTTP POST request bytes like a
          // StepOutputFragment does. So once we've seen the final fragment in
          // the POST, we've read all the bytes from it
          .watchTermination() { (mat, doneFuture) =>
            doneFuture.onComplete {
              case Success(_) => self ! WorkerTask.DoneProcessingFragments(mat, asker)
              case Failure(err) => err.printStackTrace(); asker ! Status.Failure(err)
            }
          }
          .runWith(sink)
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
  case class ProcessFragments(fragments: Source[StepOutputFragment, Any], sink: Sink[ConvertOutputElement, akka.NotUsed])
  private case class UpdateState(state: StepOutputFragmentCollector.State)
  private case class DoneProcessingFragments(materializedValue: Any, asker: ActorRef)

  def props(
    stepOutputFragmentCollector: StepOutputFragmentCollector,
    task: WrittenFile2,
    workerIdleTimeout: FiniteDuration,
    timeoutActorRef: ActorRef
  ) = Props(
    classOf[WorkerTask],
    stepOutputFragmentCollector,
    task,
    workerIdleTimeout,
    timeoutActorRef
  )
}
