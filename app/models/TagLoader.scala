package models

import anorm._
import anorm.SqlParser._
import java.sql.Connection

class TagLoader {
  
  def loadByName(name: String)(implicit c: Connection) : Option[Long] = {
    SQL("SELECT id FROM tag WHERE name = {name}").on("name" -> name).
      as(scalar[Option[Long]] single)
  }

}