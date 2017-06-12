package modules

import akka.actor.ActorSystem
import akka.util.Timeout
import redis.RedisClient
import javax.inject.{Inject,Singleton}
import play.api.Configuration
import play.api.inject.ApplicationLifecycle
import scala.concurrent.Future

class RedisConfiguration(private val config: Configuration) {
  private def getString(key: String): String = config
      .getString(key)
      .getOrElse(throw new Exception(s"Missing configuration value $key"))

  private def getInt(key: String): Int = config
      .getInt(key)
      .getOrElse(throw new Exception(s"Missing configuration value $key"))

  val host: String = getString("redis.host")
  val port: Int = getInt("redis.port")
}

/** Provides a RedisClient. */
@Singleton
class RedisModule @Inject() (actorSystem: ActorSystem, configuration: Configuration, lifecycle: ApplicationLifecycle) {
  lazy val client: RedisClient = {
    val config = new RedisConfiguration(configuration)
    val ret = new RedisClient(config.host, config.port)(actorSystem)

    lifecycle.addStopHook { () => Future.successful(client.stop) }

    ret
  }
}
