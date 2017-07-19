package com.overviewdocs.akkautil

import akka.actor.{Actor,ActorRef,Props}
import scala.reflect.runtime.universe.WeakTypeTag
import scala.collection.mutable.Queue

/** Queues messages of type `BrokerActor.Work[T]`, only passing the message
  * along to another Actor when requested with `BrokerActor.WorkerReady`.
  */
class BrokerActor[T] extends Actor {
  val todo: Queue[BrokerActor.Work[T]] = Queue.empty
  val workers: Queue[ActorRef] = Queue.empty

  override def receive: Actor.Receive = {
    case work: BrokerActor.Work[T] => {
      workers.dequeueFirst(_ => true) match {
        case None => todo.enqueue(work)
        case Some(worker) => worker ! work
      }
    }
    case BrokerActor.WorkerReady => {
      todo.dequeueFirst(_ => true) match {
        case None => workers.enqueue(sender)
        case Some(work) => sender ! work
      }
    }
  }
}

object BrokerActor {
  /** Sent from WorkerActor to BrokerActor to request Work. */
  case object WorkerReady

  /** Sent from BrokerActor to WorkerActor in response to request. */
  case class Work[T](message: T, asker: ActorRef)(implicit tt: WeakTypeTag[T])

  def props[T]: Props = Props(new BrokerActor[T])
}
