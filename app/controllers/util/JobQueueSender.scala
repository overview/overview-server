package controllers.util

import akka.actor.ActorSelection

import com.overviewdocs.messages.DocumentSetCommands
import modules.RemoteActorSystemModule

trait JobQueueSender {
  protected def documentSetWorkerActor: ActorSelection

  def send(command: DocumentSetCommands.Command): Unit = {
    documentSetWorkerActor ! command
  }
}

object JobQueueSender extends JobQueueSender {
  override protected lazy val documentSetWorkerActor = {
    play.api.Play.current.injector.instanceOf[RemoteActorSystemModule].documentSetWorkerActor
  }
}
