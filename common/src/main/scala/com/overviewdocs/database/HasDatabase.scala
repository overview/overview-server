package com.overviewdocs.database

/** Dependency injection: provides a Database.
  *
  * Users can use dependency injection like this:
  *
  * ```
  * object ThingLister extends HasDatabase {
  *   import database.api._
  *   import com.overviewdocs.models.Thing
  *   import com.overviewdocs.models.tables.Things
  *
  *   def things: Future[Seq[Thing]] = database.seq(Things)
  * }
  * ```
  */
trait HasDatabase {
  /** The database. */
  protected lazy val database: Database = Database()
}
