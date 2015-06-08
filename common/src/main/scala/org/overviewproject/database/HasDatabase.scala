package org.overviewproject.database

/** Dependency injection: provides a Database.
  *
  * Users can use dependency injection like this:
  *
  * ```
  * trait ThingLister extends HasDatabase {
  *   import databaseApi._
  *   import org.overviewproject.models.Thing
  *   import org.overviewproject.models.tables.Things
  *
  *   def things: Future[Seq[Thing]] = database.seq(Things)
  * }
  *
  * object ThingLister extends ThingLister with DatabaseProvider
  * ```
  */
trait HasDatabase {
  /** The database. */
  protected val database: Database

  /** Exposes the Slick Database API. */
  protected val databaseApi = org.overviewproject.database.Slick.api
}
