package org.overviewproject.database

/** Like HasDatabase, but only for ugly, obsolete code. */
trait HasBlockingDatabase {
  protected val blockingDatabase: BlockingDatabase

  /** Exposes the Slick Database API. */
  protected val blockingDatabaseApi = org.overviewproject.database.Slick.api
}
