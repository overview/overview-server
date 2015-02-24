package controllers.backend

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future
import scala.slick.jdbc.JdbcBackend.Session

import org.overviewproject.test.DbSpecification

/** Helps test Backends.
  *
  * Backends read from and write to the database. So our tests will write to
  * and read from the database. Here's how:
  *
  * class MyDbBackendSpec extends DbBackendSpecification {
  *   "MyDbBackend" should {
  *     "find a DocumentSet" in new DbScope {
  *       val backend = new TestDbBackend(session) with MyDbBackend
  *
  *       val documentSet = factory.documentSet()
  *       val foundDocumentSet = backend.findDocumentSet(documentSet.id).headOption
  *       foundDocumentSet.map(_.id) must beSome(documentSet.id)
  *     }
  *   }
  * }
  */
trait DbBackendSpecification
  extends DbSpecification
{
  class TestDbBackend(val session: Session) extends DbBackend {
    override def db[A](block: Session => A): Future[A] = Future {
      block(session)
    }
  }
}
