package com.overviewdocs.jobhandler.documentcloud

import akka.actor.{Actor,ActorRef,Props}
import scala.collection.mutable

import com.overviewdocs.messages.DocumentSetCommands

class DocumentCloudImportWorkBroker extends Actor {
  private case class Task(
    worker: ActorRef,
    ackReceiver: Option[ActorRef],
    ackMessage: Any
  )

  private case class PendingTask(
    command: DocumentSetCommands.AddDocumentsFromDocumentCloud,
    ackReceiver: Option[ActorRef],
    ackMessage: Any
  ) {
    def toTask(worker: ActorRef) = Task(worker, ackReceiver, ackMessage)
  }

  private var waitingWorkers = mutable.Queue[ActorRef]()
  private var pendingTasks = mutable.Queue[PendingTask]()
  private var runningTasks = mutable.Map[(Long,Int),Task]() // (documentSetId,documentCloudImportId) => Task

  override def receive = {
    case command: DocumentSetCommands.AddDocumentsFromDocumentCloud => {
      pendingTasks.enqueue(PendingTask(command, None, null))
      sendCommands
    }
    case DocumentCloudImportWorkBroker.DoWorkThenAck(command, ackReceiver, ackMessage) => {
      pendingTasks.enqueue(PendingTask(command, Some(ackReceiver), ackMessage))
      sendCommands
    }
    case DocumentCloudImportWorkBroker.WorkerReady => {
      waitingWorkers.enqueue(sender)
      sendCommands
    }
    case DocumentCloudImportWorkBroker.WorkerDone(command) => {
      val key = (command.documentSetId, command.documentCloudImport.id)
      runningTasks.remove(key).foreach { task =>
        task.ackReceiver.foreach { ackReceiver =>
          ackReceiver ! task.ackMessage
        }
      }
    }
  }

  private def sendCommands: Unit = {
    while (pendingTasks.nonEmpty && waitingWorkers.nonEmpty) {
      val pendingTask = pendingTasks.dequeue
      val worker = waitingWorkers.dequeue
      val command = pendingTask.command
      worker ! command
      runningTasks((command.documentSetId, command.documentCloudImport.id)) = pendingTask.toTask(worker)
    }
  }
}

object DocumentCloudImportWorkBroker {
  def props: Props = Props(new DocumentCloudImportWorkBroker)

  /** A message from a worker. */
  sealed trait WorkerMessage

  /** The sender is ready for a AddDocumentsFromDocumentCloud message. */
  case object WorkerReady extends WorkerMessage

  /** The sender finished an AddDocumentsFromDocumentCloud message. */
  case class WorkerDone(command: DocumentSetCommands.AddDocumentsFromDocumentCloud) extends WorkerMessage

  /** Do the work in `command`, then send `ackMessage` to `receiver`. */
  case class DoWorkThenAck(command: DocumentSetCommands.AddDocumentsFromDocumentCloud, receiver: ActorRef, ackMessage: Any)
}
