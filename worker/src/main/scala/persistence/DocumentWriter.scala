/*
 * DocumentWriter.scala
 *
 * Overview Project
 * Created by Jonas Karlsson, Aug 2012
 */

package persistence

import java.sql.Connection
import anorm.{ SQL, sqlToSimple, toParameterValue }
import org.overviewproject.tree.orm.Document
import org.squeryl.PrimitiveTypeMode._
import persistence.Schema.documents

/**
 * Writes out document information associated with the documentSetId
 */

object DocumentWriter {
  import persistence.Schema.documents

  def write(document: Document) {
    documents.insert(document)
  }

  def updateDescription(documentId: Long, description: String) {
    update(documents)(d =>
      where(d.id === documentId)
      set (d.title := description))
  }
}
