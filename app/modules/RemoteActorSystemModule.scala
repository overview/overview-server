package modules

import akka.actor.{ActorSelection,ActorSystem}
import akka.util.Timeout
import com.google.inject.ImplementedBy
import com.typesafe.config.ConfigFactory
import javax.inject._
import play.api.{Configuration,Environment}
import play.api.inject.ApplicationLifecycle
import scala.concurrent.Future

@ImplementedBy(classOf[DefaultRemoteActorSystemModule])
trait RemoteActorSystemModule {
  val actorSystem: ActorSystem
  val defaultTimeout: Timeout
  def messageBroker: ActorSelection
}

/** Provides helpers for communicating with the worker.
  *
  * This assumes Play's actor system is a RemoteActorSystem.
  *
  * We make <tt>actorSystem</tt> public so that clients who inject
  * RemoteActorSystemModule don't need to inject ActorSystem as well.
  */
@Singleton
class DefaultRemoteActorSystemModule @Inject() (
  configuration: Configuration,
  val actorSystem: ActorSystem
) extends RemoteActorSystemModule {
  private val hostname = configuration.getString("worker.message_broker_hostname").get
  private val port = configuration.getInt("worker.message_broker_port").get
  private val rootPath = s"akka.tcp://worker@${hostname}:${port}/user"

  override val defaultTimeout = Timeout(30, java.util.concurrent.TimeUnit.SECONDS)

  override def messageBroker = _messageBroker

  private lazy val _messageBroker = {
    actorSystem.actorSelection(rootPath + "/DocumentSetMessageBroker")
  }
}
