package com.overviewdocs.jobhandler.documentset

import akka.actor.{Actor,ActorRef,Props}
import scala.reflect.runtime.universe.WeakTypeTag
import scala.collection.mutable

import com.overviewdocs.akkautil.BrokerActor
import com.overviewdocs.messages.DocumentSetCommands.SortField
import com.overviewdocs.util.Logger

// Modeled after BrokerActor
class SortBroker extends Actor {
  private val todo: mutable.Queue[BrokerActor.Work[SortField]] = mutable.Queue.empty
  private val workers: mutable.Queue[ActorRef] = mutable.Queue.empty
  private val inProgressByWork: mutable.Set[SortField] = mutable.Set.empty
  private val inProgressByWorker: mutable.Map[ActorRef,SortField] = mutable.Map.empty

  private val logger: Logger = Logger.forClass(getClass)

  override def receive: Actor.Receive = {
    case _work: BrokerActor.Work[_] => {
      val work = _work.asInstanceOf[BrokerActor.Work[SortField]]
      workers.dequeueFirst(_ => true) match {
        case None => todo.enqueue(work)
        case Some(worker) => sendWork(worker, work)
      }
    }
    case BrokerActor.WorkerReady => {
      inProgressByWorker.remove(sender) match {
        case None => {}
        case Some(message) => inProgressByWork.remove(message)
      }

      todo.dequeueFirst(_ => true) match {
        case None => workers.enqueue(sender)
        case Some(work) => sendWork(sender, work)
      }
    }
  }

  private def sendWork(worker: ActorRef, work: BrokerActor.Work[SortField]): Unit = {
    if (inProgressByWork.contains(work.message)) {
      // There's a worker already running this sort. FIXME send the asker the
      // running worker's progress reports.
      logger.warn("Skipped {} because we are already running it. The user will notice this.", work.message)
      self.tell(BrokerActor.WorkerReady, worker)
    } else {
      inProgressByWork.+=(work.message)
      inProgressByWorker.+=((worker, work.message))
      worker ! work
    }
  }
}

object SortBroker {
  def props: Props = Props(new SortBroker)
}
