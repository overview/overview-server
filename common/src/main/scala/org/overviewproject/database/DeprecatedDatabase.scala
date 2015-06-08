package org.overviewproject.database

import java.sql.Connection

/** Old, undocumented database interface.
  *
  * Yup -- we actually went three years without a documented database API.
  */
//@deprecated(message="Use Database or BlockingDatabase", since="20150606")
object DeprecatedDatabase extends TransactionProvider {
  def transactionBlock[A](block: Connection => A): A = {
    DB.withTransaction(block)
  }
}
