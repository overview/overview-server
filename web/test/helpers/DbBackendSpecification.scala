package controllers.backend

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future
import slick.jdbc.JdbcBackend.Session

import com.overviewdocs.database.Database
import com.overviewdocs.test.DbSpecification

/** Helps test Backends.
  *
  * Backends read from and write to the database. So our tests will write to
  * and read from the database. Here's how:
  *
  * class MyDbBackendSpec extends DbBackendSpecification {
  *   "MyDbBackend" should {
  *     "find a DocumentSet" in new DbScope {
  *       val backend = new MyDbBackend {}
  *
  *       val documentSet = factory.documentSet()
  *       val foundDocumentSet = backend.findDocumentSet(documentSet.id).headOption
  *       foundDocumentSet.map(_.id) must beSome(documentSet.id)
  *     }
  *   }
  * }
  */
trait DbBackendSpecification extends DbSpecification {
  trait DbBackendScope extends DbScope
}
