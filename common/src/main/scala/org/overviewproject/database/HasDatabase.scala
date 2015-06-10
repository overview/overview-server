package org.overviewproject.database

/** Dependency injection: provides a Database.
  *
  * Users can use dependency injection like this:
  *
  * ```
  * object ThingLister extends HasDatabase {
  *   import database.api._
  *   import org.overviewproject.models.Thing
  *   import org.overviewproject.models.tables.Things
  *
  *   def things: Future[Seq[Thing]] = database.seq(Things)
  * }
  * ```
  */
trait HasDatabase {
  /** The database. */
  protected lazy val database: Database = new Database(DB.getDataSource)

  /** Exposes the Slick Database API. */
  protected val databaseApi = org.overviewproject.database.Slick.api
}
