package com.overviewdocs.jobhandler.csv

import akka.actor.{Actor,ActorRef,Props}
import scala.util.{Failure,Success}

import com.overviewdocs.csv.CsvImporter
import com.overviewdocs.messages.DocumentSetCommands

class CsvImportWorker(broker: ActorRef) extends Actor {
  import context.dispatcher

  override def preStart = ready

  override def receive = {
    case command: DocumentSetCommands.AddDocumentsFromCsvImport => {
      val importer = new CsvImporter(command.csvImport)
      importer.run.onComplete {
        case Success(()) => {
          broker ! CsvImportWorkBroker.WorkerDone(command)
          ready
        }
        case Failure(ex) => self ! ex
      }
    }
  }

  private def ready: Unit = broker ! CsvImportWorkBroker.WorkerReady
}

object CsvImportWorker {
  def props(broker: ActorRef) = Props(new CsvImportWorker(broker))
}
