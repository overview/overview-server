package plugins

import akka.actor.ActorSystem
import akka.util.Timeout
import com.redis.RedisClient
import play.api.{Application,Plugin}

case class RedisConfiguration(host: String, port: Int)

object RedisConfiguration {
  def apply(application: Application): RedisConfiguration = {
    def getString(key: String): String =
      application.configuration
        .getString(key)
        .getOrElse(throw new Exception(s"Missing configuration value $key"))

    def getInt(key: String): Int =
      application.configuration
        .getInt(key)
        .getOrElse(throw new Exception(s"Missing configuration value $key"))

    val host = getString("redis.host")
    val port = getInt("redis.port")

    RedisConfiguration(host, port)
  }
}

class RedisPlugin(application: Application) extends Plugin {
  @volatile var loaded = false

  lazy val config: RedisConfiguration = RedisConfiguration(application)

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
