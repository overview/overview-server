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
  implicit val mat = {
    val decider: Supervision.Decider = {
      case ex: RuntimeException => {
        ex.printStackTrace()
        Supervision.Stop
      }
    }
    ActorMaterializer(ActorMaterializerSettings(context.system).withSupervisionStrategy(decider))
  }

  private val tickInterval = workerIdleTimeout / 10
  timers.startPeriodicTimer("tick", HttpWorker.Tick, tickInterval)

  private def isTimedOut(lastActivityAt: Instant): Boolean = {
    val timeoutTemporalAmount = JDuration.ofNanos(workerIdleTimeout.toNanos)
    lastActivityAt.plus(timeoutTemporalAmount).isBefore(Instant.now)
  }

  override def receive = idle(Instant.now, StepOutputFragmentCollector.State.Start(task))

  def idle(lastActivityAt: Instant, state: StepOutputFragmentCollector.State): Receive = {
    case HttpWorker.Tick => {
      if (isTimedOut(lastActivityAt)) {
        timeoutActorRef ! task
        context.stop(self)
      }
    }

    case HttpWorker.GetForAsker(asker) => {
      if (task.isCanceled) {
        // This is a common place the HTTP client will check for cancelation:
        // during a large POST. The HTTP client should respond to the 404 we
        // return here by ending the HTTP POST early. That's why we transition
        // to "canceling": we want to keep consuming the POST until the client
        // is done.
        asker ! None
        startCancel(state)
        context.become(canceling)
      } else {
        asker ! Some((task, self))
        context.become(idle(Instant.now, state))
      }
    }

    case HttpWorker.ProcessFragments(fragments) => {
      context.become(processingFragments(state, sender))
      processFragments(state, fragments)
    }
  }

  def processingFragments(state: StepOutputFragmentCollector.State, asker: ActorRef): Receive = {
    case HttpWorker.Tick => {} // We never timeout while there's an HTTP connection open
    case HttpWorker.UpdateState(state) => {
      context.become(processingFragments(state, asker))
    }

    case HttpWorker.GetForAsker(asker) => {
      if (task.isCanceled) {
        asker ! None
        context.become(wantCancel(state, asker))
      } else {
        asker ! Some((task, self))
      }
    }

    case HttpWorker.ProcessFragments(fragments) => {
      sender ! Status.Failure(new RuntimeException("You are already POSTing from this worker. Don't POST twice simultaneously."))
    }

    case HttpWorker.DoneProcessingFragments => {
      asker ! Status.Success(akka.Done)
      if (state.isInstanceOf[StepOutputFragmentCollector.State.End]) {
        context.stop(self)
      } else {
        context.become(idle(Instant.now, state))
      }
    }
  }

  def wantCancel(state: StepOutputFragmentCollector.State, asker: ActorRef): Receive = {
    case HttpWorker.Tick => {} // We never timeout while there's an HTTP connection open
    case HttpWorker.UpdateState(state) => context.become(wantCancel(state, asker))
    case HttpWorker.GetForAsker(asker) => asker ! None
    case HttpWorker.ProcessFragments(_) => {
      sender ! Status.Failure(new RuntimeException("You are already POSTing from this worker. Don't POST twice simultaneously."))
    }
    case HttpWorker.DoneProcessingFragments => {
      asker ! Status.Success(akka.Done)
      startCancel(state)
      context.become(canceling)
    }
  }

  def canceling: Receive = {
    case HttpWorker.Tick => {}
    case HttpWorker.GetForAsker(asker) => asker ! None
    case HttpWorker.ProcessFragments(_) => {
      sender ! Status.Failure(new RuntimeException("Canceling")) // will this race ever happen, in practice?
    }
  }

  private def processFragments(state: StepOutputFragmentCollector.State, fragments: Source[StepOutputFragment, akka.NotUsed]): Unit = {
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
          case Success(_) => {
            self ! HttpWorker.DoneProcessingFragments
          }
          case Failure(err) => {
            // This is a critical error. We have no idea what to do.
            err.printStackTrace()
            self ! HttpWorker.DoneProcessingFragments
          }
        }
      }
      .runWith(sink)
  }

  private def startCancel(state: StepOutputFragmentCollector.State): Unit = {
    state match {
      case StepOutputFragmentCollector.State.End(_) => context.stop(self)
      case _ => stepOutputFragmentCollector.transitionState(state, StepOutputFragment.Canceled).onComplete {
        case Success(newState) => {
          Source(newState.toEmit)
            .watchTermination() { (_, futureDone) =>
              futureDone.onComplete {
                case _ => context.stop(self)
              }
            }
            .runWith(sink)
        }
        case Failure(ex) => {
          ex.printStackTrace()
          ???
        }
      }
    }
  }
}

object HttpWorker {
  private case object Tick
  case class GetForAsker(asker: ActorRef)
  case class ProcessFragments(fragments: Source[StepOutputFragment, akka.NotUsed])
  private case class UpdateState(state: StepOutputFragmentCollector.State)
  private case object DoneProcessingFragments

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
