package modules

import akka.actor.{ActorSelection,ActorSystem}
import com.typesafe.config.ConfigFactory
import javax.inject._
import play.api.{Configuration,Environment}
import play.api.inject.ApplicationLifecycle
import scala.concurrent.Future

/** Provides helpers for communicating with the worker.
  *
  * This assumes Play's actor system is a RemoteActorSystem.
  *
  * We make <tt>actorSystem</tt> public so that clients who inject
  * RemoteActorSystemModule don't need to inject ActorSystem as well.
  */
@Singleton
class RemoteActorSystemModule @Inject() (configuration: Configuration, val actorSystem: ActorSystem) {
  private val hostname = configuration.getString("worker.message_broker_hostname").get
  private val port = configuration.getInt("worker.message_broker_port").get
  private val rootPath = s"akka.tcp://worker@${hostname}:${port}/user"

  lazy val messageBroker: ActorSelection = {
    actorSystem.actorSelection(rootPath + "/DocumentSetMessageBroker")
  }
}
