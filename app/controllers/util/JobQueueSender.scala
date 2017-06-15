package controllers.util

import akka.actor.ActorSelection
import javax.inject.Inject

import com.overviewdocs.messages.DocumentSetCommands
import modules.RemoteActorSystemModule

class JobQueueSender @Inject() (remoteActorSystemModule: RemoteActorSystemModule) {
  protected def messageBroker: ActorSelection = remoteActorSystemModule.messageBroker

  def send(command: DocumentSetCommands.Command): Unit = {
    messageBroker ! command
  }
}
