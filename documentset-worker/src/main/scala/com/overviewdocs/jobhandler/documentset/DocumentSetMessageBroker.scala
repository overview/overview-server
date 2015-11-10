package com.overviewdocs.jobhandler.documentset

import akka.actor.{Actor,ActorRef,Props}
import scala.collection.mutable

import com.overviewdocs.messages.DocumentSetCommands
import com.overviewdocs.util.Logger

/** Ensures DocumentSetCommands are processed in serial, FIFO-ordered, per
  * document set.
  *
  * Each worker must request a command using the `WorkerReady` message. That
  * signals the worker will process one command. The worker must respond with
  * `WorkerDoneDocumentSetCommand` to free up the DocumentSet.
  */
class DocumentSetMessageBroker extends Actor {
  private class DocumentSetInfo(val documentSetId: Long) {
    var worker: Option[ActorRef] = None
    val commands = mutable.Queue[DocumentSetCommands.Command]()
  }

  private val logger = Logger.forClass(getClass)
  private val documentSets = mutable.Map[Long,DocumentSetInfo]()
  private val readyDocumentSets = mutable.Queue[DocumentSetInfo]()
  private val readyWorkers = mutable.Queue[ActorRef]() // could be Set, but Queue is easier to test

  override def receive = {
    case command: DocumentSetCommands.CancelCommand => {
      logger.info("Received CancelCommand: {}", command)
      // Just forward the command to the worker, if there is one. Nothing more.
      documentSets.get(command.documentSetId).flatMap(_.worker).foreach { worker =>
        worker ! command
      }
    }
    case command: DocumentSetCommands.Command => {
      logger.info("Received Command: {}", command)
      if (documentSets.contains(command.documentSetId)) {
        // We don't care whether this is in readyDocumentSets or not: one more
        // command doesn't change the logic.
        documentSets(command.documentSetId).commands.enqueue(command)
      } else {
        val info = new DocumentSetInfo(command.documentSetId)
        info.commands.enqueue(command)
        documentSets(info.documentSetId) = info
        readyDocumentSets.enqueue(info)
        sendIfAvailable
      }
    }
    case DocumentSetMessageBroker.WorkerDoneDocumentSetCommand(documentSetId) => {
      val info = documentSets(documentSetId)
      if (info.commands.nonEmpty) {
        info.worker = None
        readyDocumentSets.enqueue(info)
        sendIfAvailable
      } else {
        documentSets.-=(documentSetId)
      }
    }
    case DocumentSetMessageBroker.WorkerReady => {
      readyWorkers.enqueue(sender)
      sendIfAvailable
    }
  }

  /** Sends one message to one worker, if there is a message and a live worker.
    *
    * Side-effects:
    * * Removes an element from `readyDocumentSets`
    * * Removes an element from `readyWorkers`
    */
  private def sendIfAvailable: Unit = {
    if (readyDocumentSets.nonEmpty && readyWorkers.nonEmpty) {
      val worker = readyWorkers.dequeue
      val info = readyDocumentSets.dequeue
      val command = info.commands.dequeue
      info.worker = Some(worker)
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
