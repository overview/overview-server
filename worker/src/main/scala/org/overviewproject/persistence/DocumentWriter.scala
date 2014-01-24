/*
 * DocumentWriter.scala
 *
 * Overview Project
 * Created by Jonas Karlsson, Aug 2012
 */

package org.overviewproject.persistence

import org.overviewproject.persistence.orm.Schema.documents
import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.Document

/**
 * Writes out document information associated with the documentSetId
 */

object DocumentWriter {

  def write(document: Document) {
    documents.insert(document)
  }

  def updateDescription(documentId: Long, description: String) {
    update(documents)(d =>
      where(d.id === documentId)
      set (d.description := description))
  }
}
