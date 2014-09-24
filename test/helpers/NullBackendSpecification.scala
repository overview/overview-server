package controllers.backend

import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.{Await,Future}

import org.overviewproject.test.factories.{Factory,PodoFactory}

/** Helps test Backends.
  *
  * An example:
  *
  * class MyNullBackendSpec extends NullBackendSpecification {
  *   "MyNullBackend" should {
  *     "create something" in new DbScope {
  *       val backend = new TestNullBackend with MyNullBackend
  *
  *       val documentSet = factory.documentSet()
  *       val result = await(backend.create(documentSet.id))
  *       result.documentSetId must beEqualTo(documentSet.id)
  *     }
  *   }
  * }
  */
trait NullBackendSpecification
  extends Specification
{
  sequential

  class TestNullBackend extends Backend

  trait NullScope extends Scope {
    lazy val factory: Factory = PodoFactory
    def await[A](f: Future[A]) = Await.result(f, Duration(2, "seconds"))
  }
}
