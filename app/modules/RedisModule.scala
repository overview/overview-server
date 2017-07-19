package modules

import akka.actor.ActorSystem
import akka.util.Timeout
import redis.RedisClient
import javax.inject.{Inject,Singleton}
import play.api.Configuration
import play.api.inject.ApplicationLifecycle
import scala.concurrent.Future

case class RedisConfiguration(host: String, port: Int)

/** Provides a RedisClient. */
@Singleton
class RedisModule @Inject() (actorSystem: ActorSystem, configuration: Configuration, lifecycle: ApplicationLifecycle) {
  lazy val client: RedisClient = {
    val config = RedisConfiguration(configuration.get[String]("redis.host"), configuration.get[Int]("redis.port"))
    val ret = new RedisClient(config.host, config.port)(actorSystem)

    lifecycle.addStopHook { () => Future.successful(client.stop) }

    ret
  }
}
