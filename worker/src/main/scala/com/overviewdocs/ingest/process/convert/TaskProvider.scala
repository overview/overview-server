package com.overviewdocs.ingest.process.convert

import akka.actor.{Actor,ActorRef,Props,Timers}
import java.time.Instant
import java.util.UUID // for random IDs
import scala.collection.immutable.Queue
import scala.concurrent.duration.FiniteDuration

/** TaskProvider: respond to Asks with an Option[Task] (backed by a Source[Task])
  *
  * Why not just use a Source? Because these are connected to incoming
  * HttpRequests. An HttpRequest only lasts a certain amount of time, after
  * which its connection times out and any answers to it are lost. The outcome
  * in HttpTaskServer would be that newly-provided Tasks would be sent as
  * responses to timed-out HttpRequests, which would in turn make the Tasks
  * time out, as no workers would ever receive their IDs.
  *
  * Instead, TaskProvider replies within `timeout`. That means each HttpRequest
  * will receive a timely response, even if that response is `None`.
  */
class TaskProvider(
  timeout: FiniteDuration
) extends Actor with Timers {
  import context._

  private case class QueuedAsk(id: UUID, asker: ActorRef)
  private object QueuedAsk {
    def apply(asker: ActorRef) = new QueuedAsk(UUID.randomUUID, asker)
  }

  def empty(queuedAsks: Queue[QueuedAsk]): Receive = {
    case TaskProvider.Init => {
      sender ! TaskProvider.Ack
    }
    case task: Task => queuedAsks.dequeueOption match {
      case Some((firstQueuedAsk, nextAskers)) => {
        timers.cancel(firstQueuedAsk.id.toString)
        firstQueuedAsk.asker ! Some(task)
        sender ! TaskProvider.Ack
        become(empty(nextAskers))
      }
      case None => {
        become(full(task, sender))
      }
    }
    case TaskProvider.Ask => {
      val queuedAsk = QueuedAsk(sender)
      timers.startSingleTimer(queuedAsk.id.toString, TaskProvider.Tick, timeout)
      become(empty(queuedAsks.enqueue(queuedAsk)))
    }
    case TaskProvider.Tick => {
      // timers.cancel() guarantees the Tick never arrives here. So we know
      // this Tick is for a QueuedAsk that was never received. It must be the
      // oldest QueuedAsk -- otherwise, another Tick would have arrived first.
      val (firstAsker, nextAskers) = queuedAsks.dequeue // or crash -- how would it be empty?
      firstAsker.asker ! None
      become(empty(nextAskers))
    }
    case TaskProvider.Complete => {
      timers.cancelAll
      queuedAsks.foreach(queuedAsk => queuedAsk.asker ! None)
      context.stop(self)
    }
  }

  def full(task: Task, sinkActor: ActorRef): Receive = {
    case TaskProvider.Complete => become(draining(task))
    case TaskProvider.Ask => {
      sender ! Some(task)
      sinkActor ! TaskProvider.Ack
      become(empty(Queue.empty[QueuedAsk]))
    }
  }

  def draining(task: Task): Receive = {
    case TaskProvider.Ask => {
      sender ! Some(task)
      context.stop(self)
    }
  }

  override def receive = empty(Queue.empty)
}

object TaskProvider {
  case object Init
  case object Ack
  case object Complete
  case object Ask
  case object Tick

  def props(timeout: FiniteDuration) = Props(classOf[TaskProvider], timeout)
}
