package com.overviewdocs.jobhandler.csv

import akka.actor.{Actor,ActorRef,Props}
import scala.collection.mutable

import com.overviewdocs.messages.DocumentSetCommands

class CsvImportWorkBroker extends Actor {
  private case class Task(
    worker: ActorRef,
    ackReceiver: Option[ActorRef],
    ackMessage: Any
  )

  private case class PendingTask(
    command: DocumentSetCommands.AddDocumentsFromCsvImport,
    ackReceiver: Option[ActorRef],
    ackMessage: Any
  ) {
    def toTask(worker: ActorRef) = Task(worker, ackReceiver, ackMessage)
  }

  private var waitingWorkers = mutable.Queue[ActorRef]()
  private var pendingTasks = mutable.Queue[PendingTask]()
  private var runningTasks = mutable.Map[(Long,Long),Task]() // (documentSetId,csvImportId) => Task

  override def receive = {
    case command: DocumentSetCommands.AddDocumentsFromCsvImport => {
      pendingTasks.enqueue(PendingTask(command, None, null))
      sendCommands
    }
    case CsvImportWorkBroker.DoWorkThenAck(command, ackReceiver, ackMessage) => {
      pendingTasks.enqueue(PendingTask(command, Some(ackReceiver), ackMessage))
      sendCommands
    }
    case CsvImportWorkBroker.WorkerReady => {
      waitingWorkers.enqueue(sender)
      sendCommands
    }
    case CsvImportWorkBroker.WorkerDone(command) => {
      val key = (command.documentSetId, command.csvImport.id)
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
      runningTasks((command.documentSetId, command.csvImport.id)) = pendingTask.toTask(worker)
    }
  }
}

object CsvImportWorkBroker {
  def props: Props = Props(new CsvImportWorkBroker)

  /** A message from a worker. */
  sealed trait WorkerMessage

  /** The sender is ready for a AddDocumentsFromCsvImport message. */
  case object WorkerReady extends WorkerMessage

  /** The sender finished an AddDocumentsFromCsvImport message. */
  case class WorkerDone(command: DocumentSetCommands.AddDocumentsFromCsvImport) extends WorkerMessage

  /** Do the work in `command`, then send `ackMessage` to `receiver`. */
  case class DoWorkThenAck(command: DocumentSetCommands.AddDocumentsFromCsvImport, receiver: ActorRef, ackMessage: Any)
}
