package controllers.util

import akka.actor.{ActorSelection,ActorSystem}
import com.typesafe.config.ConfigFactory

import com.overviewdocs.messages.{ClusterCommands,DocumentSetCommands}

trait JobQueueSender {
  protected def documentSetWorkerActor: ActorSelection
  protected def clusterQueueActor: ActorSelection

  def send(command: DocumentSetCommands.Command): Unit = {
    documentSetWorkerActor ! command
  }

  def send(command: ClusterCommands.Command): Unit = {
    clusterQueueActor ! command
  }
}

object JobQueueSender extends JobQueueSender {
  override protected lazy val documentSetWorkerActor = find("DocumentSetMessageBroker")
  override protected lazy val clusterQueueActor = find("FileGroupJobManager")

  private lazy val config = ConfigFactory.load.getConfig("documentset-worker")
  private lazy val hostname = config.getString("message_broker_hostname")
  private lazy val port = config.getInt("message_broker_port")
  private lazy val rootPath = s"akka.tcp://WorkerActorSystem@${hostname}:${port}/user/supervised"

  private lazy val remoteSystem = ActorSystem.create("documentset-worker", config)

  private def find(name: String): ActorSelection = {
    remoteSystem.actorSelection(rootPath + "/"+ name)
  }
}
