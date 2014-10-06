package controllers.backend

import akka.actor.ActorSystem
import akka.util.Timeout
import com.redis.RedisClient
import org.specs2.mutable.Specification
import org.specs2.specification.{Fragments,Scope,Step}
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.{Await,Future}

import org.overviewproject.test.factories.{Factory,PodoFactory}

/** Helps test Backends.
  *
  * An example:
  *
  * class MyRedisBackendSpec extends RedisBackendSpecification {
  *   "MyRedisBackend" should {
  *     "create something" in new RedisScope {
  *       val backend = new TestRedisBackend(redis) with MyRedisBackend
  *
  *       val documentSet = factory.documentSet()
  *       val result = await(backend.create(documentSet.id))
  *       result.documentSetId must beEqualTo(documentSet.id)
  *     }
  *   }
  * }
  */
trait RedisBackendSpecification
  extends Specification
{
  sequential

  implicit val duration: FiniteDuration = FiniteDuration(2, "seconds")
  implicit val timeout: Timeout = Timeout(duration)

  lazy val actorSystem = ActorSystem("RedisBackendSpecification")
  lazy val redis = {
    implicit val sys = actorSystem
    RedisClient("localhost", 9020)
  }

  private lazy val _redis = redis

  def setupRedis = {
    Await.result(redis.flushdb, duration)
  }

  def shutdownRedis = {
    Await.result(redis.flushdb, duration)
    actorSystem.shutdown()
    actorSystem.awaitTermination()
  }

  override def map(fs: => Fragments) = {
    Step(setupRedis) ^ super.map(fs) ^ Step(shutdownRedis)
  }

  class TestRedisBackend extends RedisBackend {
    override protected lazy val redis: RedisClient = _redis
  }

  trait RedisScope extends Scope {
    lazy val factory: Factory = PodoFactory
    def await[A](f: Future[A]) = Await.result(f, duration)
    setupRedis
  }
}
