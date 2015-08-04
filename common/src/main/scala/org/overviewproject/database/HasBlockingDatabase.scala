package com.overviewdocs.database

/** Like HasDatabase, but only for ugly code (and nice unit tests).
  *
  * To make code less ugly:
  *
  * 1. Search for HasBlockingDatabase
  * 2. Replace it with HasDatabase
  * 3. Fix everything that breaks (without `await()` calls, of course, as they
  *    are even uglier)
  *
  * Ideally, only DbSpecification would have a `blockingDatabase`.
  */
trait HasBlockingDatabase extends HasDatabase {
  protected lazy val blockingDatabase: BlockingDatabase = new BlockingDatabase(database)
}
