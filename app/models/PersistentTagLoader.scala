package models

import anorm._
import anorm.SqlParser._
import java.sql.Connection

class PersistentTagLoader {
  
  def loadByName(name: String)(implicit c: Connection) : Option[Long] = {
    SQL("SELECT id FROM tag WHERE name = {name}").on("name" -> name).
      as(scalar[Long] *).headOption
  }

  def countDocuments(id: Long)(implicit c: Connection) : Long = {
    SQL("SELECT COUNT(*) from document_tag WHERE tag_id = {tagId}").
      on("tagId" -> id).as(scalar[Long] single)
  }
}