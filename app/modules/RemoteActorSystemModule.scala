package modules

import akka.actor.{ActorSelection,ActorSystem}
import com.typesafe.config.ConfigFactory
import javax.inject._
import play.api.Environment
import play.api.inject.ApplicationLifecycle
import scala.concurrent.Future

/** Provides a RemoteActorSystem, to communicate with worker.
  *
  * We have three behaviors:
  *
  * * In production, the actor system starts on first reference and stops on
  *   shutdown.
  * * In dev, the actor system stops every time the app shuts down. (This is
  *   why we *need* this module: otherwise we try to listen on the same port
  *   every time the server restarts.)
  * * In test, we never ever reference this ActorSystem.
  */
@Singleton
class RemoteActorSystemModule @Inject() (environment: Environment, lifecycle: ApplicationLifecycle) {
  private lazy val config = ConfigFactory.load.getConfig("worker")
  private lazy val hostname = config.getString("message_broker_hostname")
  private lazy val port = config.getInt("message_broker_port")
  private lazy val rootPath = s"akka.tcp://worker@${hostname}:${port}/user"

  lazy val remoteActorSystem: ActorSystem = {
    val ret = ActorSystem.create("worker", config, environment.classLoader)

    lifecycle.addStopHook { () =>
      ret.terminate
      Future.successful(())
    }

    ret
  }

  lazy val workerActor: ActorSelection = {
    remoteActorSystem.actorSelection(rootPath + "/DocumentSetMessageBroker")
  }
}
