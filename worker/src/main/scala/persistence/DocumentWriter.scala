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

  def updateDescription(id: Long, description: String)(implicit c: Connection): Long = {
    SQL("UPDATE document SET title = {description} WHERE id = {id}").
      on("description" -> description, "id" -> id).executeUpdate()
  }
}
