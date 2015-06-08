package controllers.backend

import scala.concurrent.Future

import org.overviewproject.database.exceptions.Conflict
import org.overviewproject.models.DocumentSetUser
import org.overviewproject.models.DocumentSetUser.Role
import org.overviewproject.models.tables.DocumentSetUsers

trait DocumentSetUserBackend {
  /** Returns non-owner DocumentSetUsers for a given DocumentSet. */
  def index(documentSetId: Long): Future[Seq[DocumentSetUser]]

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

trait DbDocumentSetUserBackend extends DocumentSetUserBackend with DbBackend {
  import databaseApi._
  import DocumentSetUsers.roleColumnType // XXX clean this up

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
    implicit val ec = database.executionContext

    database.run(inserter.+=(DocumentSetUser(documentSetId, userEmail, Role(false))))
      .map(Some(_))
      .recover({ case e: Conflict => None })
  }

  override def destroy(documentSetId: Long, userEmail: String) = {
    database.delete(byAll(documentSetId, userEmail))
  }
}

object DocumentSetUserBackend
  extends DbDocumentSetUserBackend
  with org.overviewproject.database.DatabaseProvider
