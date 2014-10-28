package controllers.backend

import play.api.libs.json.JsObject
import scala.concurrent.Future

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

trait DbStoreBackend extends StoreBackend { self: DbBackend =>
  import org.overviewproject.database.Slick.simple._

  lazy val tokenToStore = Compiled { (token: Column[String]) =>
    Stores.filter(_.apiToken === token)
  }

  lazy val tokenToStoreJson = Compiled { (token: Column[String]) =>
    Stores.filter(_.apiToken === token).map(_.json)
  }

  private lazy val inserter = {
    val q = for { stores <- Stores } yield (stores.apiToken, stores.json)
    q.insertInvoker
  }

  private def insertSync(token: String, json: JsObject)(session: Session): Unit = {
    inserter.insert((token, json))(session)
  }

  private def updateSync(token: String, json: JsObject)(session: Session): Int = {
    tokenToStoreJson(token).update(json)(session)
  }

  override def showOrCreate(token: String) = db { session =>
    val maybeExistingStore = tokenToStore(token).firstOption(session)
    maybeExistingStore match {
      case None => {
        // Insert without RETURNING, so a race won't be a big deal
        exceptions.wrap { insertSync(token, JsObject(Seq()))(session) }
        // Now there _must_ be an object...
        tokenToStore(token).first(session)
      }
      case Some(existingStore) => existingStore
    }
  }

  override def upsert(token: String, json: JsObject) = db { session =>
    // There is no UPDATE RETURNING
    val nUpdated = updateSync(token, json)(session)
    nUpdated match {
      case 0 => {
        // Insert without RETURNING, so a race won't be a big deal
        exceptions.wrap { insertSync(token, json)(session) }
        // Now there _must_ be an object...
        tokenToStore(token).first(session)
      }
      case _ => tokenToStore(token).first(session)
    }
  }

  override def destroy(token: String) = db { session =>
    tokenToStore(token).delete(session)
  }
}

object StoreBackend extends DbStoreBackend with DbBackend
