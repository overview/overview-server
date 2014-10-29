package controllers.backend

import scala.concurrent.Future

import org.overviewproject.models.ApiToken
import org.overviewproject.models.tables.ApiTokens

trait ApiTokenBackend {
  /** Creates an ApiToken with a secure, unique token.
    *
    * Returns an error if the database write fails.
    */
  def create(documentSetId: Long, attributes: ApiToken.CreateAttributes): Future[ApiToken]

  /** Destroys an ApiToken.
    *
    * Throws MissingParent when a Store or View depends on this ApiToken.
    */
  def destroy(token: String): Future[Unit]
}

trait DbApiTokenBackend extends ApiTokenBackend { self: DbBackend =>
  import org.overviewproject.database.Slick.simple._

  lazy val byIdCompiled = Compiled { token: Column[String] =>
    ApiTokens.filter(_.token === token)
  }

  lazy val insertInvoker = (ApiTokens returning ApiTokens).insertInvoker

  override def create(documentSetId: Long, attributes: ApiToken.CreateAttributes) = db { session =>
    val apiToken = ApiToken.build(documentSetId, attributes)
    exceptions.wrap {
      insertInvoker.insert(apiToken)(session)
    }
  }

  override def destroy(token: String) = db { session =>
    exceptions.wrap {
      byIdCompiled(token).delete(session)
    }
  }
}

object ApiTokenBackend extends DbApiTokenBackend with DbBackend
