package com.overviewdocs.akkautil

import akka.actor.{Actor,ActorRef}
import scala.concurrent.Future
import scala.reflect.runtime.universe.WeakTypeTag
import scala.util.{Success,Failure}

/** Runs one async task at a time by pulling work from a BrokerActor.
  *
  * By default, `preStart()` will ask `broker` for work
  * (`BrokerActor.WorkerReady`). When the broker sends work
  * (`BrokerActor.Work`), this WorkerActor will call `doWorkAsync()` to process
  * the message and then ask the broker for another task once the work is done.
  *
  * Concrete implementations must override `doWorkAsync()`. They must also set
  * `T`, the type of message to process.
  *
  * `doWorkAsync()` is called with a "`asker`" ActorRef. That's the forwarded
  * message's `sender`.
  *
  * If `doWorkAsync()` fails, it sends its Exception to the asker. (If the asker
  * neglects that possibility, akka will report a dead letter, or the asker will
  * report an unhandled message -- both of which should log lots of context so
  * we get warning emails.)
  */
abstract class WorkerActor[T: WeakTypeTag](val broker: ActorRef) extends Actor {
  /** The actual implementation. */
  protected def doWorkAsync(message: T, asker: ActorRef): Future[Unit]

  import context.dispatcher

  override def preStart: Unit = ready

  override def receive: Actor.Receive = {
    case BrokerActor.Work(message, asker) => {
      doWorkAsync(message.asInstanceOf[T], asker).onComplete {
        case Success(()) => ready
        case Failure(ex) => {
          asker ! ex
          ready
        }
      }
    }
  }

  private def ready: Unit = {
    broker ! BrokerActor.WorkerReady
  }
}
