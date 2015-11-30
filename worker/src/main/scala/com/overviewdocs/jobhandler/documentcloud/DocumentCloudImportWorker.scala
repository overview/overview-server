package com.overviewdocs.jobhandler.documentcloud

import akka.actor.{Actor,ActorRef,Props}
import scala.util.{Failure,Success}

import com.overviewdocs.documentcloud.Main
import com.overviewdocs.messages.DocumentSetCommands

class DocumentCloudImportWorker(broker: ActorRef) extends Actor {
  import context.dispatcher

  override def preStart = ready

  override def receive = {
    case command: DocumentSetCommands.AddDocumentsFromDocumentCloud => {
      Main.run(command.documentCloudImport).onComplete {
        case Success(()) => {
          broker ! DocumentCloudImportWorkBroker.WorkerDone(command)
          ready
        }
        case Failure(ex) => self ! ex
      }
    }
  }

  private def ready: Unit = broker ! DocumentCloudImportWorkBroker.WorkerReady
}

object DocumentCloudImportWorker {
  def props(broker: ActorRef) = Props(new DocumentCloudImportWorker(broker))
}
