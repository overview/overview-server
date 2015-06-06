package org.overviewproject.clone

import anorm._
import java.sql.Connection

import org.overviewproject.database.DeprecatedDatabase

trait InDatabaseCloner {
  val DocumentSetIdMask: Long = 0x00000000FFFFFFFFl

  def cloneQuery: SqlQuery

  def clone(sourceDocumentSetId: Long, cloneDocumentSetId: Long): Boolean = {
    implicit val c: Connection = DeprecatedDatabase.currentConnection

    cloneQuery.on(
      "cloneDocumentSetId" -> cloneDocumentSetId,
      "sourceDocumentSetId" -> sourceDocumentSetId,
      "documentSetIdMask" -> DocumentSetIdMask).execute
  }

}
