package controllers.backend

import scala.concurrent.Future
import scala.util.control.Exception.catching

import org.overviewproject.models.DocumentSetUser
import org.overviewproject.models.DocumentSetUser.Role
import org.overviewproject.models.tables.DocumentSetUsers

trait DocumentSetUserBackend {
  /** Returns non-owner DocumentSetUsers for a given DocumentSet. */
  def index(documentSetId: Long): Future[Seq[DocumentSetUser]]

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

trait DbDocumentSetUserBackend extends DocumentSetUserBackend { self: DbBackend =>
  import org.overviewproject.database.Slick.simple._
  import DocumentSetUsers.roleColumnType

  private val byDocumentSetId = Compiled { (documentSetId: Column[Long]) =>
    DocumentSetUsers
      .filter(_.documentSetId === documentSetId)
      .filter(_.role === Role(false))
      .sortBy(_.userEmail)
  }

  private val byAll = Compiled { (documentSetId: Column[Long], userEmail: Column[String]) =>
    DocumentSetUsers
      .filter(_.documentSetId === documentSetId)
      .filter(_.userEmail === userEmail)
      .filter(_.role === Role(false))
  }

  override def index(documentSetId: Long) = list(byDocumentSetId(documentSetId))

  override def update(documentSetId: Long, userEmail: String) = db { session =>
    val row = DocumentSetUser(documentSetId, userEmail, Role(false))
    catching(classOf[exceptions.Conflict]).opt(exceptions.wrap {
      (DocumentSetUsers returning DocumentSetUsers).+=(row)(session)
    })
  }

  override def destroy(documentSetId: Long, userEmail: String) = db { session =>
    byAll(documentSetId, userEmail)
      .delete(session)
  }
}

object DocumentSetUserBackend extends DbDocumentSetUserBackend with DbBackend
