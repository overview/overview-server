package com.overviewdocs.database

/** Static dependency injection: provides a Database.
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
  *
  * (If you're using dynamic dependency injection, you don't need this trait.)
  */
trait HasDatabase {
  /** The database. */
  protected lazy val database: Database = Database()
}
