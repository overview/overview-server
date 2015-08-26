package modules

import akka.actor.ActorSystem
import akka.util.Timeout
import com.redis.RedisClient
import javax.inject._
import play.api.inject.ApplicationLifecycle
import play.api.{Mode,Play}
import scala.concurrent.Future

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

/** Data that persists between Application invocations.
  *
  * This stores our RedisClient and actor system so that they persist across
  * Play applications.
  */
object RedisModuleGlobalState {
  lazy val connect: (ActorSystem, RedisClient) = {
    // Assume Play.current is set -- how else did we call this?
    val config = RedisConfiguration

    implicit val sys = ActorSystem("RedisModule")
    implicit val ec = sys.dispatcher
    implicit val timeout = Timeout.longToTimeout(5000)

    (sys, RedisClient(config.host, config.port))
  }
}

/** Provides a RedisClient.
  *
  * We want certain behaviors:
  *
  * * In dev mode, application shutdown dereferences the ClassLoader, so we
  *   lose all variables; we need to disconnect before that happens or the
  *   ActorSystem will continue running.
  * * In test mode, every test runs in the same ClassLoader but the lifecycle
  *   hooks still apply. We want to reuse the same RedisClient, because that's
  *   faster than starting up and shutting down hundreds of actor systems.
  * * In production, it doesn't matter because we never shut down. Do whatever
  *   is easiest.
  */
@Singleton
class RedisModule @Inject() (lifecycle: ApplicationLifecycle) {
  lazy val client: RedisClient = {
    val (actorSystem, ret) = RedisModuleGlobalState.connect

    // Assume Play is running -- otherwise, who called this code?
    if (Play.current.mode == Mode.Dev) {
      lifecycle.addStopHook { () =>
        actorSystem.shutdown
        Future.successful(())
      }
    }

    ret
  }
}
