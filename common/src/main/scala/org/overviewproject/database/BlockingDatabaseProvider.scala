package org.overviewproject.database

/** Gives a blockingDatabase value.
  *
  * (This is like dependency injection, but there is no HasBlockingDatabase
  * because this class implies deprecated code.)
  */
trait BlockingDatabaseProvider extends HasBlockingDatabase {
  override protected lazy val blockingDatabase = new BlockingDatabase(X.publicDatabase)

  private object X extends DatabaseProvider {
    def publicDatabase = database
  }
}
