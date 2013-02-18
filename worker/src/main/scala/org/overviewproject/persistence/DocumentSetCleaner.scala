/*
 * DocumentSetCleaner.scala
 *
 * Overview Project
 * Created by Jonas Karlsson, Oct 2012
 */

package org.overviewproject.persistence

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
    val nodeDocumentUpdate = SQL("""
      DELETE FROM node_document WHERE node_id IN
        (SELECT id FROM node WHERE document_set_id = {id})
      """)
    val nodeUpdate = SQL("DELETE FROM node WHERE document_set_id = {id}")

    updateOnDocumentSet(nodeDocumentUpdate, documentSetId)
    updateOnDocumentSet(nodeUpdate, documentSetId)
  }

  private def removeDocumentData(documentSetId: Long)(implicit c: Connection) {
    val update = SQL("DELETE FROM document WHERE document_set_id = {id}")
    updateOnDocumentSet(update, documentSetId)
  }

  // Assumes query wants to bind "id" to documentSetId
  private def updateOnDocumentSet(query: anorm.SqlQuery, documentSetId: Long)(implicit c: Connection): Long =
    query.on("id" -> documentSetId).executeUpdate
}
