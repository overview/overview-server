package modules

import akka.actor.ActorSystem
import akka.util.Timeout
import com.redis.RedisClient
import javax.inject._
import play.api.inject.ApplicationLifecycle
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

/** Data that persists between Application invocations.
  *
  * This stores our RedisClient and actor system so that they persist across
  * Play applications.
  */
object RedisModuleGlobalState {
  var config: RedisConfiguration = _

  lazy val connect: (ActorSystem, RedisClient) = {
    implicit val sys = ActorSystem("RedisModule")
    implicit val ec = sys.dispatcher
    implicit val timeout = Timeout(5, java.util.concurrent.TimeUnit.SECONDS)

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
class RedisModule @Inject() (lifecycle: ApplicationLifecycle, configuration: Configuration) {
  lazy val client: RedisClient = {
    RedisModuleGlobalState.config = new RedisConfiguration(configuration)
    val (actorSystem, ret) = RedisModuleGlobalState.connect

    lifecycle.addStopHook { () =>
      actorSystem.terminate
      Future.successful(())
    }

    ret
  }
}
