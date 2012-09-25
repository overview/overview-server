/*
 * DocumentWriter.scala
 *
 * Overview Project
 * Created by Jonas Karlsson, Aug 2012
 */

package persistence

import anorm._
import java.sql.Connection

/**
 * Writes out document information associated with the documentSetId
 */
class DocumentWriter(documentSetId: Long) {

  def write(title: String, documentCloudId: String)(implicit c: Connection): Long = {
    SQL("""
        INSERT INTO document (title, documentcloud_id, document_set_id) VALUES
          ({title}, {documentCloudId}, {documentSetId})
        """).on("title" -> title, "documentCloudId" -> documentCloudId,
                "documentSetId" -> documentSetId).executeInsert().get
  }

  def updateDescription(id: Long, description: String)(implicit c: Connection): Long = {
    SQL("UPDATE document SET title = {description} WHERE id = {id}").
      on("description" -> description, "id" -> id).executeUpdate()
  }
}
