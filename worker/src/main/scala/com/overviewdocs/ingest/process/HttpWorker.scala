package com.overviewdocs.ingest.process

import akka.actor.{Actor,ActorRef,Props,Status,Timers}
import akka.http.scaladsl.model.{HttpRequest,HttpResponse}
import akka.stream.{ActorMaterializer,ActorMaterializerSettings,Supervision}
import akka.stream.scaladsl.{Sink,Source}
import java.time.{Duration=>JDuration,Instant}
import scala.concurrent.duration.{Duration,FiniteDuration}
import scala.concurrent.{ExecutionContext,Future}
import scala.util.{Success,Failure}

import com.overviewdocs.ingest.model.{WrittenFile2,ConvertOutputElement}

/** The server's understanding of the state of a (HTTP-client) worker.
  *
  * Notice how little state we store. We don't identify the HTTP client or any
  * concurrent HTTP requests. We don't even know the task ID. All we do is
  * pipe all input to the Task.
  *
  * Inputs into this Actor:
  *
  * * GetForAsker(asker) -- sends Some((task, self)) to asker; stops itself
  *                         (and generates StepOutputFragment.Canceled) if task
  *                         is canceled.
  * * Tick -- checks lastActivityAt; can send to `timeoutActorRef` or generate a `StepOutputFragment.Canceled`.
  * * stop -- when the parent stops this Actor, the job is complete.
  */
class HttpWorker(
  stepOutputFragmentCollector: StepOutputFragmentCollector,
  task: WrittenFile2,
  sink: Sink[ConvertOutputElement, akka.NotUsed],

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
  timers.startPeriodicTimer("tick", HttpWorker.Tick, tickInterval)

  private var processingFragments: Boolean = false
  private var fragmentCollectorState: StepOutputFragmentCollector.State = StepOutputFragmentCollector.State.Start(task)
  private var lastActivityAt = Instant.now

  private def isTimedOut(lastActivityAt: Instant): Boolean = {
    val timeoutTemporalAmount = JDuration.ofNanos(workerIdleTimeout.toNanos)
    lastActivityAt.plus(timeoutTemporalAmount).isBefore(Instant.now)
  }

  override def receive = become(idle(Instant.now, StepOutputFragmentCollector.State.Start(task)))

  def idle(lastActivityAt: Instant, state: StepOutputFragmentCollector.State): Receive = {
    case HttpWorker.Canceled => context.stop(self)

    case HttpWorker.Tick => {
      if (isTimedOut(lastActivityAt)) {
        timeoutActorRef ! task
        context.stop(self)
      }
    }

    case HttpWorker.GetForAsker(asker) => {
      if (task.isCanceled) {
        asker ! None
        startCancel
        become(canceling(None))
      } else {
        asker ! Some((task, self))
        become(idle(Instant.now, state))
      }
    }

    case HttpWorker.ProcessFragments(fragments) => {
      processFragments(fragments)
      become(processingFragments(state, sender))
    }
  }

  def processingFragments(state: StepOutputFragmentCollector.State, asker: ActorRef): Receive = {
    case Tick => {} // We never timeout while there's an HTTP connection open

    case HttpWorker.GetForAsker(asker) => {
      if (task.isCanceled) {
        asker ! None
        become(canceling(Some(asker)))
      } else {
        asker ! Some((task, self))
      }
    }

    case HttpWorker.UpdateState(state) => {
      updateLastActivity
      fragmentCollectorState = state
    }

    case HttpWorker.ProcessFragments(fragments) => {
      sender ! Status.Failure("You are already POSTing from this worker. Don't POST twice simultaneously.")
    }

    case HttpWorker.DoneProcessingFragments => {
      asker ! Status.Success(akka.Done)
      if (fragmentCollectorState.isInstanceOf[StepOutputFragmentCollector.State.End]) {
        context.stop(self)
      } else {
        become(idle(Instant.now, state))
      }
    }
  }

  def canceling(askerOpt: Option[ActorRef]): Receive = {
    case Tick => {}
    case HttpWorker.GetForAsker(asker) => asker ! None
    case HttpWorker.UpdateState(_) => {}
    case HttpWorker.ProcessFragments(_) => {
      sender ! Status.Failure("You are already POSTing from this worker. Don't POST twice simultaneously.")
    }
    case HttpWorker.DoneProcessingFragments => {

    }
  }

  private def processFragments(fragments): Unit = {
    val decider: Supervision.Decider = {
      case ex: RuntimeException => ex.printStackTrace(); Supervision.Stop
    }
    implicit val mat = ActorMaterializer(ActorMaterializerSettings(context.system).withSupervisionStrategy(decider))

    fragments
      .scanAsync[StepOutputFragmentCollector.State](state)(stepOutputFragmentCollector.transitionState)
      .drop(1) // scanAsync outputs its initial State; we don't want it
      .mapConcat { state =>
        self ! HttpWorker.UpdateState(state)
        state.toEmit
      }
      // Any ConvertOutputElement at this point is "safe" to read -- it
      // does not depend on upstream HTTP POST request bytes like a
      // StepOutputFragment does. So once we've seen the final fragment in
      // the POST, we've read all the bytes from it
      .watchTermination() { (mat, doneFuture) =>
        doneFuture.onComplete {
          case Success(_) => self ! HttpWorker.DoneProcessingFragments
          case Failure(err) => {
            // This is a critical error. We have no idea what to do.
            err.printStackTrace()
            asker ! Status.Failure(err)
            timeoutActorRef ! task
            context.stop(self)
          }
        }
      }
      .runWith(sink)
  }
}

object HttpWorker {
  private case object Tick
  case class GetForAsker(asker: ActorRef)
  case class ProcessFragments(fragments: Source[StepOutputFragment, Any])
  private case class UpdateState(state: StepOutputFragmentCollector.State)
  private case object DoneProcessingFragments
  private case object Canceled

  def props(
    stepOutputFragmentCollector: StepOutputFragmentCollector,
    task: WrittenFile2,
    sink: Sink[ConvertOutputElement, akka.NotUsed],
    workerIdleTimeout: FiniteDuration,
    timeoutActorRef: ActorRef
  ) = Props(
    classOf[HttpWorker],
    stepOutputFragmentCollector,
    task,
    sink,
    workerIdleTimeout,
    timeoutActorRef
  )
}
