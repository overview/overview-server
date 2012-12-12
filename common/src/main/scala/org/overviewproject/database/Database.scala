package org.overviewproject.database

import java.sql.Connection

object Database extends TransactionProvider {

  def transactionBlock[A](block: Connection => A): A = {
    DB.withTransaction(block)
  }

}