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

trait DbStoreObjectBackend extends StoreObjectBackend with DbBackend {
  import databaseApi._

  private lazy val byIdCompiled = Compiled { (id: Rep[Long]) =>
    StoreObjects.filter(_.id === id)
  }

  private lazy val byStoreIdCompiled = Compiled { (storeId: Rep[Long]) =>
    StoreObjects
      .filter(_.storeId === storeId)
  }

  private lazy val byStoreIdAndIndexedLongCompiled = Compiled { (storeId: Rep[Long], indexedLong: Rep[Long]) =>
    StoreObjects
      .filter(_.storeId === storeId)
      .filter(_.indexedLong === indexedLong)
  }

  private lazy val byStoreIdAndIndexedStringCompiled = Compiled { (storeId: Rep[Long], indexedString: Rep[String]) =>
    StoreObjects
      .filter(_.storeId === storeId)
      .filter(_.indexedString === indexedString)
  }

  private lazy val byStoreIdAndIndexedLongAndIndexedStringCompiled = Compiled { (storeId: Rep[Long], indexedLong: Rep[Long], indexedString: Rep[String]) =>
    StoreObjects
      .filter(_.storeId === storeId)
      .filter(_.indexedLong === indexedLong)
      .filter(_.indexedString === indexedString)
  }

  private def byStoreIdAndIndexes(storeId: Long, indexedLong: Option[Long], indexedString: Option[String]) = {
    indexedLong match {
      case None => indexedString match {
        case None => byStoreIdCompiled(storeId)
        case Some(s) => byStoreIdAndIndexedStringCompiled(storeId, s)
      }
      case Some(i) => indexedString match {
        case None => byStoreIdAndIndexedLongCompiled(storeId, i)
        case Some(s) => byStoreIdAndIndexedLongAndIndexedStringCompiled(storeId, i, s)
      }
    }
  }

  override def index(storeId: Long, indexedLong: Option[Long]=None, indexedString: Option[String]=None) = {
    database.seq(byStoreIdAndIndexes(storeId, indexedLong, indexedString))
  }

  override def show(id: Long) = database.option(byIdCompiled(id))

  private lazy val storeObjectsForInsert = {
    for { o <- StoreObjects } yield (o.storeId, o.indexedLong, o.indexedString, o.json)
  }
  protected lazy val inserter = (storeObjectsForInsert returning StoreObjects)

  override def create(storeId: Long, attributes: StoreObject.CreateAttributes) = {
    database.run(inserter.+=(storeId, attributes.indexedLong, attributes.indexedString, attributes.json))
  }

  override def createMany(storeId: Long, attributesSeq: Seq[StoreObject.CreateAttributes]) = {
    val tuples = for {
      a <- attributesSeq
    } yield (storeId, a.indexedLong, a.indexedString, a.json)

    database.run(inserter.++=(tuples))
  }

  private lazy val updateQuery = Compiled { (id: Rep[Long]) =>
    for (v <- StoreObjects if v.id === id) yield (v.indexedLong, v.indexedString, v.json)
  }
  override def update(id: Long, attributes: StoreObject.UpdateAttributes) = {
    val q = updateQuery(id).update(attributes.indexedLong, attributes.indexedString, attributes.json)
      .andThen(byIdCompiled(id).result.headOption)
    database.run(q)
  }

  override def destroy(id: Long) = {
    /*
     * We run two DELETEs in a single query, to simulate a transaction and
     * avoid a round trip.
     */
    database.runUnit(sqlu"""
      WITH subdelete AS (
        DELETE FROM document_store_object
        WHERE store_object_id = $id
        RETURNING 1
      )
      DELETE FROM store_object
      WHERE id = $id
    """)
  }

  override def destroyMany(storeId: Long, ids: Seq[Long]) = {
    /*
     * We run two DELETEs in a single query, to simulate a transaction and
     * avoid a round trip.
     */
    database.runUnit(sqlu"""
      WITH ids AS (
        SELECT *
        FROM (VALUES #${ids.map("(" + _ + ")").mkString(",")}) AS t(id)
        WHERE id IN (SELECT id FROM store_object WHERE store_id = $storeId)
      ),
      subdelete AS (
        DELETE FROM document_store_object
        WHERE store_object_id IN (SELECT id FROM ids)
        RETURNING 1
      )
      DELETE FROM store_object
      WHERE id IN (SELECT id FROM ids)
        AND (SELECT COUNT(*) FROM subdelete) IS NOT NULL
    """)
  }
}

object StoreObjectBackend extends DbStoreObjectBackend with org.overviewproject.database.DatabaseProvider
