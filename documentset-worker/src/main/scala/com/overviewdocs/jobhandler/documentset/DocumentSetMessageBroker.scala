package com.overviewdocs.jobhandler.documentset

import akka.actor.{Actor,ActorRef}
import scala.collection.mutable

import com.overviewdocs.messages.DocumentSetCommands
import com.overviewdocs.util.Logger

/** Ensures DocumentSetCommands are processed in serial, FIFO-ordered, per
  * document set.
  *
  * Each worker must request a command using the `WorkerReady` message. That
  * signals the worker will process one command. When the command is processed,
  * the worker must send another `WorkerReady` message to receive the next
  * command.
  *
  * This is a dummy implementation: it just queues all commands and assumes
  * there's only one worker.
  */
class DocumentSetMessageBroker extends Actor {
  private val logger = Logger.forClass(getClass)
  private val queue = mutable.Queue[DocumentSetCommands.Command]()
  private val readyWorkers = mutable.Queue[ActorRef]() // Queue is nice because it has a dequeue() command

  override def receive = {
    case command: DocumentSetCommands.Command => {
      logger.info("Brokering DocumentSet command: {}", command)
      queue.enqueue(command)
      sendIfAvailable
    }
    case DocumentSetMessageBroker.WorkerReady => {
      readyWorkers.enqueue(sender)
      sendIfAvailable
    }
  }

  /** Sends one message to one worker, if there is a message and a live worker.
    *
    * Side-effects:
    * * Removes an element from `queue`
    * * Removes an element from `readyWorkers`
    */
  private def sendIfAvailable: Unit = {
    if (queue.nonEmpty && readyWorkers.nonEmpty) {
      readyWorkers.dequeue ! queue.dequeue
    }
  }
}

object DocumentSetMessageBroker {
  case object WorkerReady
}
