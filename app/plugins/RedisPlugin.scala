package plugins

import akka.actor.ActorSystem
import akka.util.Timeout
import com.redis.RedisClient
import play.api.{Application,Plugin}
import play.api.Play

trait RedisConfiguration {
  val host: String
  val port: Int
}

object RedisConfiguration extends RedisConfiguration {
  private def getString(key: String): String =
    Play.current.configuration
      .getString(key)
      .getOrElse(throw new Exception(s"Missing configuration value $key"))

  private def getInt(key: String): Int =
    Play.current.configuration
      .getInt(key)
      .getOrElse(throw new Exception(s"Missing configuration value $key"))

  val host: String = getString("redis.host")
  val port: Int = getInt("redis.port")
}

class RedisPlugin extends Plugin {
  @volatile var loaded = false

  lazy val config: RedisConfiguration = RedisConfiguration

  lazy val system: ActorSystem = {
    loaded = true
    ActorSystem("RedisPlugin")
  }

  lazy val client: RedisClient = {
    implicit val sys = system
    implicit val ec = system.dispatcher
    implicit val timeout = Timeout.longToTimeout(5000)
    RedisClient(config.host, config.port)
  }

  override def onStart: Unit = {}
  override def onStop: Unit = {
    if (loaded) {
      system.shutdown()
    }
  }
}
