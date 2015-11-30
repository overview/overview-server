package controllers.util

import akka.actor.ActorSelection

import com.overviewdocs.messages.DocumentSetCommands
import modules.RemoteActorSystemModule

trait JobQueueSender {
  protected def workerActor: ActorSelection

  def send(command: DocumentSetCommands.Command): Unit = {
    workerActor ! command
  }
}

object JobQueueSender extends JobQueueSender {
  override protected lazy val workerActor = {
    play.api.Play.current.injector.instanceOf[RemoteActorSystemModule].workerActor
  }
}
