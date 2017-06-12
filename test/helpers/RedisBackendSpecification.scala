package controllers.backend

import akka.actor.ActorSystem
import akka.util.Timeout
import redis.RedisClient
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.Play
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.{Await,Future}

import com.overviewdocs.test.factories.{Factory,PodoFactory}
import modules.RedisModule

/** Helps test Backends.
  *
  * An example:
  *
  * class MyRedisBackendSpec extends RedisBackendSpecification {
  *   "MyRedisBackend" should {
  *     "create something" in new RedisScope {
  *       val backend = new TestRedisBackend with MyRedisBackend
  *
  *       val documentSet = factory.documentSet()
  *       val result = await(backend.create(documentSet.id))
  *       result.documentSetId must beEqualTo(documentSet.id)
  *     }
  *   }
  * }
  */
trait RedisBackendSpecification extends test.helpers.InAppSpecification { self =>
  sequential

  implicit val duration: FiniteDuration = FiniteDuration(2, "seconds")
  implicit val timeout: Timeout = Timeout(duration)

  trait RedisScope extends Scope {
    lazy val redisModule = app.injector.instanceOf[RedisModule]
    lazy val redis = redisModule.client
    lazy val factory: Factory = PodoFactory
    def await[A](f: Future[A]) = Await.result(f, duration)
    await(redis.flushdb)
  }
}
