package controllers.backend

import com.google.inject.ImplementedBy
import javax.inject.Inject
import scala.collection.immutable
import scala.concurrent.Future

import com.overviewdocs.database.Database
import com.overviewdocs.database.exceptions.Conflict
import com.overviewdocs.models.DocumentSetUser
import com.overviewdocs.models.DocumentSetUser.Role
import com.overviewdocs.models.tables.DocumentSetUsers

@ImplementedBy(classOf[DbDocumentSetUserBackend])
trait DocumentSetUserBackend {
  /** Returns non-owner DocumentSetUsers for a given DocumentSet. */
  def index(documentSetId: Long): Future[immutable.Seq[DocumentSetUser]]

  /** Adds an owner DocumentSetUser.
    *
    * Error if the DocumentSetUser already exists.
    */
  def createOwner(documentSetId: Long, userEmail: String): Future[DocumentSetUser]

  /** Adds or modifies a non-owner DocumentSetUser.
    *
    * Returns the DocumentSetUser if it was added or modified. (If there is
    * already an owner with that email address, returns None.)
    */
  def update(documentSetId: Long, userEmail: String): Future[Option[DocumentSetUser]]

  /** Ensures the given non-owner DocumentSetUser does not exist.
    *
    * no-op if the DocumentSetUser already does not exist or is an owner.
    */
  def destroy(documentSetId: Long, userEmail: String): Future[Unit]
}

class DbDocumentSetUserBackend @Inject() (
  val database: Database
) extends DocumentSetUserBackend with DbBackend {
  import database.api._
  import database.executionContext

  private val byDocumentSetId = Compiled { (documentSetId: Rep[Long]) =>
    DocumentSetUsers
      .filter(_.documentSetId === documentSetId)
      .filter(_.role === Role(false))
      .sortBy(_.userEmail)
  }

  private val byAll = Compiled { (documentSetId: Rep[Long], userEmail: Rep[String]) =>
    DocumentSetUsers
      .filter(_.documentSetId === documentSetId)
      .filter(_.userEmail === userEmail)
      .filter(_.role === Role(false))
  }

  protected val inserter = (DocumentSetUsers returning DocumentSetUsers)

  override def index(documentSetId: Long) = database.seq(byDocumentSetId(documentSetId))

  override def createOwner(documentSetId: Long, userEmail: String) = {
    database.run(inserter.+=(DocumentSetUser(documentSetId, userEmail, Role(true))))
  }

  override def update(documentSetId: Long, userEmail: String) = {
    database.run(inserter.+=(DocumentSetUser(documentSetId, userEmail, Role(false))))
      .map(Some(_))
      .recover({ case e: Conflict => None })
  }

  override def destroy(documentSetId: Long, userEmail: String) = {
    database.delete(byAll(documentSetId, userEmail))
  }
}
