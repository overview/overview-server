package com.overviewdocs.jobhandler.documentset

import akka.actor.{Actor,ActorRef,Props}
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
  private val readyCommands = mutable.Queue[DocumentSetCommands.Command]()
  private val readyWorkers = mutable.Queue[ActorRef]() // Queue is nice because it has a dequeue() command
  private val busyWorkers = mutable.Map[ActorRef,Long]()
  private val busyDocumentSets = mutable.Map[Long,ActorRef]()

  override def receive = {
    case command: DocumentSetCommands.CancelCommand => {
      logger.info("Received CancelCommand: {}", command)
      busyDocumentSets.get(command.documentSetId).foreach { worker =>
        worker ! command
      }
    }
    case command: DocumentSetCommands.Command => {
      logger.info("Received Command: {}", command)
      readyCommands.enqueue(command)
      sendIfAvailable
    }
    case DocumentSetMessageBroker.WorkerReady => {
      readyWorkers.enqueue(sender)
      sendIfAvailable
    }
    case DocumentSetMessageBroker.WorkerDoneDocumentSetCommand(documentSetId) => {
      busyWorkers.remove(sender)
      busyDocumentSets.remove(documentSetId)
    }
  }

  /** Sends one message to one worker, if there is a message and a live worker.
    *
    * Side-effects:
    * * Removes an element from `readyCommands`
    * * Removes an element from `readyWorkers`
    */
  private def sendIfAvailable: Unit = {
    if (readyCommands.nonEmpty && readyWorkers.nonEmpty) {
      val worker = readyWorkers.dequeue
      val command = readyCommands.dequeue
      busyWorkers(worker) = command.documentSetId
      busyDocumentSets(command.documentSetId) = worker
      worker ! command
    }

    if (readyWorkers.nonEmpty) {
      logger.info("Worker {} idling", readyWorkers.head)
    }
  }
}

object DocumentSetMessageBroker {
  def props: Props = Props(new DocumentSetMessageBroker)

  /** A message from a worker. */
  sealed trait WorkerMessage

  /** The worker requests a command. */
  case object WorkerReady extends WorkerMessage

  /** The worker has completed a command on the given DocumentSet.
    *
    * This should inspire the message broker to "unlock" the DocumentSet, so
    * its next command can be processed.
    */
  case class WorkerDoneDocumentSetCommand(documentSetId: Long) extends WorkerMessage
}
