package org.overviewproject.database

/** Provides an implementation of HasDatabase.
  *
  * This uses a singleton global variable.
  */
trait DatabaseProvider extends HasDatabase {
  override protected lazy val database: Database = new Database(DB.getDataSource)
}
