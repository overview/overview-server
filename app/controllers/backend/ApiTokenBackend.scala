package controllers.backend

import scala.concurrent.Future

import org.overviewproject.models.ApiToken
import org.overviewproject.models.tables.ApiTokens

trait ApiTokenBackend {
  /** Returns all ApiTokens with the given access.
    */
  def index(createdBy: String, documentSetId: Option[Long]): Future[Seq[ApiToken]]

  /** Returns one ApiToken.
    *
    * Neat point about authentication: if a user can supply an ApiToken, the
    * user has access to the ApiToken, because it's a password.
    */
  def show(token: String): Future[Option[ApiToken]]

  /** Creates an ApiToken with a secure, unique token.
    *
    * Returns an error if the database write fails.
    */
  def create(documentSetId: Option[Long], attributes: ApiToken.CreateAttributes): Future[ApiToken]

  /** Destroys an ApiToken.
    *
    * Throws MissingParent when a Store or View depends on this ApiToken.
    */
  def destroy(token: String): Future[Unit]
}

trait DbApiTokenBackend extends ApiTokenBackend { self: DbBackend =>
  import org.overviewproject.database.Slick.simple._

  lazy val byCreatedByAndDocumentSetIdCompiled = Compiled { (createdBy: Column[String], documentSetId: Column[Option[Long]]) =>
    ApiTokens
      .filter(row => row.documentSetId === documentSetId || (row.documentSetId.isEmpty && documentSetId.isEmpty))
      .filter(_.createdBy === createdBy)
  }

  lazy val byTokenCompiled = Compiled { token: Column[String] =>
    ApiTokens.filter(_.token === token)
  }

  lazy val insertInvoker = (ApiTokens returning ApiTokens).insertInvoker

  override def index(createdBy: String, documentSetId: Option[Long]) = {
    list(byCreatedByAndDocumentSetIdCompiled(createdBy, documentSetId))
  }

  override def show(token: String) = firstOption(byTokenCompiled(token))

  override def create(documentSetId: Option[Long], attributes: ApiToken.CreateAttributes) = db { session =>
    val apiToken = ApiToken.build(documentSetId, attributes)
    exceptions.wrap {
      insertInvoker.insert(apiToken)(session)
    }
  }

  override def destroy(token: String) = db { session =>
    exceptions.wrap {
      byTokenCompiled(token).delete(session)
    }
  }
}

object ApiTokenBackend extends DbApiTokenBackend with DbBackend
