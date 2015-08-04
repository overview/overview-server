/*
 * DocumentWriter.scala
 *
 * Overview
 * Created by Jonas Karlsson, Aug 2012
 */

package com.overviewdocs.persistence

import com.overviewdocs.database.HasBlockingDatabase
import com.overviewdocs.models.tables.{Documents,DocumentsImpl}

/**
 * Writes out document information associated with the documentSetId
 */
object DocumentWriter extends HasBlockingDatabase {
  import database.api._
  import DocumentsImpl.keywordColumnType

  protected val updater = Compiled { documentId: Rep[Long] =>
    Documents.filter(_.id === documentId).map(_.keywords)
  }

  def updateDescription(documentId: Long, description: String): Unit = {
    blockingDatabase.runUnit(updater(documentId).update(description.split(" ")))
  }
}
