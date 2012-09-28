package models

import anorm._
import java.sql.Connection

class PersistentTagSaver {

  def save(documentSetId: Long, name: String)(implicit c: Connection): Option[Long] = {
    try {
      SQL("""
          INSERT INTO tag (name, document_set_id)
          VALUES ({name}, {documentSetId})
          """).on("name" -> name, "documentSetId" -> documentSetId).executeInsert()
    } catch { case _ => None }
  }

  def delete(id: Long)(implicit c: Connection): Long = {
    val docs = SQL("DELETE FROM document_tag WHERE tag_id = {tagId}").on("tagId" -> id).executeUpdate
    val tag = SQL("DELETE FROM tag WHERE id = {tagId}").on("tagId" -> id).executeUpdate

    docs + tag
  }
}
