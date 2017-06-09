package modules

import akka.actor.ActorSystem
import akka.util.Timeout
import com.redis.RedisClient
import javax.inject.{Inject,Singleton}
import play.api.Configuration
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

/** Provides a RedisClient.
  *
  * We don't need to worry about the Lifecycle here. The Redis client runs in
  * an ActorSystem: once the ActorSystem dies it's gone.
  */
@Singleton
class RedisModule @Inject() (actorSystem: ActorSystem, configuration: Configuration) {
  lazy val client: RedisClient = {
    implicit val ec = actorSystem.dispatcher
    implicit val timeout = Timeout(5, java.util.concurrent.TimeUnit.SECONDS)

    val config = new RedisConfiguration(configuration)
    RedisClient(config.host, config.port)(actorSystem)
  }
}
