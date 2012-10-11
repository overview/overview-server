/*
 * DocumentSetCleaner.scala
 *
 * Overview Project
 * Created by Jonas Karlsson, Oct 2012
 */

package persistence

import anorm._
import java.sql.Connection

/**
 * Deletes all data associated with a document set in the database
 * but leaves the document set itself.
 */
class DocumentSetCleaner {

  /** remove node and document data associated with specified documentSetId */
  def clean(documentSetId: Long)(implicit c: Connection) {
    removeNodeData(documentSetId)
    removeDocumentData(documentSetId)
  }

  private def removeNodeData(documentSetId: Long)(implicit c: Connection) {
    SQL("""
      DELETE FROM node_document WHERE node_id IN
        (SELECT id FROM node WHERE document_set_id = {id})
      """).on("id" -> documentSetId).executeUpdate
    SQL("DELETE FROM node WHERE document_set_id = {id}").on("id" -> documentSetId).executeUpdate
  }

  private def removeDocumentData(documentSetId: Long)(implicit c: Connection) {
    SQL("DELETE FROM document WHERE document_set_id = {id}").on("id" -> documentSetId).executeUpdate
  }
}
