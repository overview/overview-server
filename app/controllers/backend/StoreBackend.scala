package controllers.backend

import play.api.libs.json.JsObject
import scala.concurrent.Future
import scala.util.{Failure,Success}

import org.overviewproject.database.exceptions
import org.overviewproject.models.tables.Stores
import org.overviewproject.models.Store

/** Stores stores.
  *
  * Any valid API token grants access to a single Store object, which holds
  * arbitrary data. That Store object is created on first use.
  */
trait StoreBackend {
  /** Creates a Store if necessary, and returns it. */
  def showOrCreate(apiToken: String): Future[Store]

  /** Creates a Store if necessary, updates it, and returns it. */
  def upsert(apiToken: String, json: JsObject): Future[Store]

  /** Destroys a Store if it exists. */
  def destroy(apiToken: String): Future[Unit]
}

trait DbStoreBackend extends StoreBackend with DbBackend {
  import databaseApi._

  lazy val tokenToStore = Compiled { (token: Rep[String]) =>
    Stores.filter(_.apiToken === token)
  }

  lazy val tokenToStoreJson = Compiled { (token: Rep[String]) =>
    Stores.filter(_.apiToken === token).map(_.json)
  }

  private lazy val inserter = (Stores.map(s => (s.apiToken, s.json)))

  private def insertIgnoringDuplicate(token: String, json: JsObject): DBIO[_] = {
    // Handle races in case two users try to INSERT at the same time. In the
    // event of a race, both INSERTs must succeed: that means we ignore the
    // value from RETURNING and absorb any error.
    inserter.+=(token, json)
      .asTry
      .map(_ match {
        case Success(v) => v
        case Failure(t) => {
          database.wrapException(t) match {
            case _: exceptions.Conflict => () // make the failed INSERT succeed
            case _ => throw t // it's a real problem.
          }
        }
      })(database.executionContext)
  }

  private def lookup(token: String): DBIO[Store] = {
    // Throws an error if the row is not there.
    tokenToStore(token).result.head
  }

  override def showOrCreate(token: String) = {
    database.run {
      tokenToStore(token).result.headOption
        .flatMap(_ match {
          case None => insertIgnoringDuplicate(token, JsObject(Seq())).andThen(lookup(token))
          case Some(existingStore) => DBIO.successful(existingStore)
        })(database.executionContext)
    }
  }

  override def upsert(token: String, json: JsObject) = {
    // There is no UPDATE RETURNING
    database.run {
      tokenToStoreJson(token).update(json)
        .flatMap(_ match {
          case 0 => insertIgnoringDuplicate(token, json).andThen(lookup(token))
          case _ => lookup(token)
        })(database.executionContext)
    }
  }

  override def destroy(token: String) = {
    /*
     * We run three DELETEs in a single query, to simulate a transaction and
     * avoid round trips.
     */
    database.runUnit(sqlu"""
      WITH store_ids AS (
        SELECT id
        FROM store
        WHERE api_token = $token
      ), store_object_ids AS (
        SELECT id
        FROM store_object
        WHERE store_id IN (SELECT id FROM store_ids)
      ), subdelete1 AS (
        DELETE FROM document_store_object
        WHERE store_object_id IN (SELECT id FROM store_object_ids)
        RETURNING 1
      ), subdelete2 AS (
        DELETE FROM store_object
        WHERE store_id IN (SELECT id FROM store_ids)
        RETURNING 1
      )
      DELETE FROM store
      WHERE id IN (SELECT id FROM store_ids)
    """)
  }
}

object StoreBackend extends DbStoreBackend with org.overviewproject.database.DatabaseProvider
