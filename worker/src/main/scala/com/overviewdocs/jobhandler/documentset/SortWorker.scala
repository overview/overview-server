package com.overviewdocs.jobhandler.documentset

import akka.actor.{ActorRef,ActorSystem,Props}
import scala.concurrent.Future

import com.overviewdocs.akkautil.WorkerActor
import com.overviewdocs.messages.DocumentSetCommands.SortField

class SortWorker(broker: ActorRef, sortRunner: SortRunner) extends WorkerActor[SortField](broker) {
  override def doWorkAsync(command: SortField, asker: ActorRef): Future[Unit] = {
    sortRunner.run(command.documentSetId, command.fieldName, asker)(context.system)
  }
}

object SortWorker {
  def props(broker: ActorRef, sortRunner: SortRunner): Props = Props(new SortWorker(broker, sortRunner))
}
