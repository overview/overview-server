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
}

trait DbApiTokenBackend extends ApiTokenBackend { self: DbBackend =>
  override def create(documentSetId: Long, attributes: ApiToken.CreateAttributes) = db { session =>
    val apiToken = ApiToken.build(documentSetId, attributes)
    exceptions.wrap {
      DbApiTokenBackend.insert(apiToken)(session)
    }
  }
}

object DbApiTokenBackend {
  import org.overviewproject.database.Slick.simple._

  private lazy val insertApiToken = (ApiTokens returning ApiTokens).insertInvoker

  def insert(apiToken: ApiToken)(session: Session): ApiToken = {
    (insertApiToken += apiToken)(session)
  }
}

object ApiTokenBackend extends DbApiTokenBackend with DbBackend
