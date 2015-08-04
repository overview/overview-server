package com.overviewdocs.database

import java.sql.Connection

/** Old, undocumented database interface.
  *
  * Yup -- we actually went three years without a documented database API.
  */
//@deprecated(message="Use Database or BlockingDatabase", since="20150606")
object DeprecatedDatabase extends TransactionProvider with HasDatabase {
  private val slickDatabase = database.slickDatabase

  protected def transactionBlock[A](block: Connection => A): A = {
    val session = slickDatabase.createSession()
    try {
      session.withTransaction { block(session.conn) }
    } finally {
      session.close()
    }
  }
}
