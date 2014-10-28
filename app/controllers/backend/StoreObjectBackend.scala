package controllers.backend

import scala.concurrent.Future

import org.overviewproject.models.StoreObject
import org.overviewproject.models.tables.StoreObjects

trait StoreObjectBackend {
  /** Lists all StoreObjects we can read.
    *
    * When indexedLong or indexedString is Some, it will filter the results.
    */
  def index(storeId: Long, indexedLong: Option[Long]=None, indexedString: Option[String]=None): Future[Seq[StoreObject]]

  /** Returns an individual StoreObject.
    *
    * Returns `None` if the StoreObject does not exist.
    */
  def show(storeObjectId: Long): Future[Option[StoreObject]]

  /** Creates a StoreObject.
    *
    * Returns an error if the database write fails.
    */
  def create(storeId: Long, attributes: StoreObject.CreateAttributes): Future[StoreObject]

  /** Creates multiple StoreObjects.
    *
    * Returns an error if the database writes fail.
    *
    * The creation is atomic: if creation number 4 fails, it will be as if
    * creations 1, 2 and 3 never happened.
    */
  def createMany(storeId: Long, attributesSeq: Seq[StoreObject.CreateAttributes]): Future[Seq[StoreObject]]

  /** Modifies a StoreObject, and returns the modified version.
    *
    * Returns `None` if the StoreObject does not exist.
    *
    * If you do not run this in a transaction, there is a potential race. This
    * method runs an UPDATE and then a SELECT. See
    * https://github.com/slick/slick/issues/963
    */
  def update(id: Long, attributes: StoreObject.UpdateAttributes): Future[Option[StoreObject]]

  /** Destroys a StoreObject.
    *
    * Returns an error if the database write fails.
    */
  def destroy(id: Long): Future[Unit]

  /** Destroys multiple StoreObjects.
    *
    * Destroys associated DocumentStoreObjects, too. Returns an error if the
    * database writes fail.
    *
    * The deletion is atomic: if it fails partway, it will be as if the request
    * never happened.
    */
  def destroyMany(storeId: Long, ids: Seq[Long]): Future[Unit]
}

trait DbStoreObjectBackend extends StoreObjectBackend { self: DbBackend =>
  import org.overviewproject.database.Slick.simple._

  private lazy val byIdCompiled = Compiled { (id: Column[Long]) =>
    StoreObjects.filter(_.id === id)
  }

  private lazy val byStoreIdCompiled = Compiled { (storeId: Column[Long]) =>
    StoreObjects
      .filter(_.storeId === storeId)
  }

  private lazy val byStoreIdAndIndexedLongCompiled = Compiled { (storeId: Column[Long], indexedLong: Column[Long]) =>
    StoreObjects
      .filter(_.storeId === storeId)
      .filter(_.indexedLong === indexedLong)
  }

  private lazy val byStoreIdAndIndexedStringCompiled = Compiled { (storeId: Column[Long], indexedString: Column[String]) =>
    StoreObjects
      .filter(_.storeId === storeId)
      .filter(_.indexedString === indexedString)
  }

  private lazy val byStoreIdAndIndexedLongAndIndexedStringCompiled = Compiled { (storeId: Column[Long], indexedLong: Column[Long], indexedString: Column[String]) =>
    StoreObjects
      .filter(_.storeId === storeId)
      .filter(_.indexedLong === indexedLong)
      .filter(_.indexedString === indexedString)
  }

  private def byStoreIdAndIndexes(storeId: Long, indexedLong: Option[Long], indexedString: Option[String])(session: Session) = {
    val query = indexedLong match {
      case None => indexedString match {
        case None => byStoreIdCompiled(storeId)
        case Some(s) => byStoreIdAndIndexedStringCompiled(storeId, s)
      }
      case Some(i) => indexedString match {
        case None => byStoreIdAndIndexedLongCompiled(storeId, i)
        case Some(s) => byStoreIdAndIndexedLongAndIndexedStringCompiled(storeId, i, s)
      }
    }
    query.list(session)
  }

  override def index(storeId: Long, indexedLong: Option[Long]=None, indexedString: Option[String]=None) = db { session =>
    byStoreIdAndIndexes(storeId, indexedLong, indexedString)(session)
  }

  override def show(id: Long) = db { session =>
    byIdCompiled(id).firstOption(session)
  }

  lazy val storeObjectsForInsert = {
    for { o <- StoreObjects } yield (o.storeId, o.indexedLong, o.indexedString, o.json)
  }
  lazy val inserter = (storeObjectsForInsert returning StoreObjects).insertInvoker

  override def create(storeId: Long, attributes: StoreObject.CreateAttributes) = db { session =>
    inserter.insert(storeId, attributes.indexedLong, attributes.indexedString, attributes.json)(session)
  }

  override def createMany(storeId: Long, attributesSeq: Seq[StoreObject.CreateAttributes]) = db { session =>
    val tuples = for {
      a <- attributesSeq
    } yield (storeId, a.indexedLong, a.indexedString, a.json)
    inserter.insertAll(tuples: _*)(session)
  }

  private lazy val updateQuery = Compiled { (id: Column[Long]) =>
    for (v <- StoreObjects if v.id === id) yield (v.indexedLong, v.indexedString, v.json)
  }
  private def updateInvoker(id: Long) = updateQuery(id).updateInvoker
  override def update(id: Long, attributes: StoreObject.UpdateAttributes) = db { session =>
    val count = updateInvoker(id).update(attributes.indexedLong, attributes.indexedString, attributes.json)(session)
    if (count > 0) byIdCompiled(id).firstOption(session) else None
  }

  override def destroy(id: Long) = db { session =>
    exceptions.wrap {
      import scala.slick.jdbc.StaticQuery.interpolation

      /*
       * We run two DELETEs in a single query, to simulate a transaction and
       * avoid a round trip.
       */
      val q = sqlu"""
        WITH subdelete AS (
          DELETE FROM document_store_object
          WHERE store_object_id = $id
          RETURNING 1
        )
        DELETE FROM store_object
        WHERE id = $id
      """

      q.execute(session)
    }
  }

  override def destroyMany(storeId: Long, ids: Seq[Long]) = db { session =>
    exceptions.wrap {
      /*
       * We run two DELETEs in a single query, to simulate a transaction and
       * avoid a round trip.
       */
      val q = s"""
        WITH ids AS (
          SELECT *
          FROM (VALUES ${ids.map("(" + _ + ")").mkString(",")}) AS t(id)
          WHERE id IN (SELECT id FROM store_object WHERE store_id = ?)
        ),
        subdelete AS (
          DELETE FROM document_store_object
          WHERE store_object_id IN (SELECT id FROM ids)
          RETURNING 1
        )
        DELETE FROM store_object
        WHERE id IN (SELECT id FROM ids)
          AND (SELECT COUNT(*) FROM subdelete) IS NOT NULL
      """

      import scala.slick.jdbc.StaticQuery
      StaticQuery.update[Long](q).apply(storeId).execute(session)
    }
  }
}

object StoreObjectBackend extends DbStoreObjectBackend with DbBackend
