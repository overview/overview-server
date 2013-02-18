/*
 * DocumentWriter.scala
 *
 * Overview Project
 * Created by Jonas Karlsson, Aug 2012
 */

package org.overviewproject.persistence

import java.sql.Connection
import anorm.{ SQL, sqlToSimple, toParameterValue }
import org.overviewproject.tree.orm.Document
import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.persistence.orm.Schema.documents

/**
 * Writes out document information associated with the documentSetId
 */

object DocumentWriter {
  import org.overviewproject.persistence.orm.Schema.documents

  def write(document: Document) {
    documents.insert(document)
  }

  def updateDescription(documentId: Long, description: String) {
    update(documents)(d =>
      where(d.id === documentId)
      set (d.description := description))
  }
}
