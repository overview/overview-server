package models

import anorm._
import anorm.SqlParser._
import java.sql.Connection

class TagLoader {
  
  def getByName(name: String)(implicit c: Connection) : Long = {
    SQL("SELECT id FROM tag WHERE name = {name}").on("name" -> name).as(scalar[Long] single)
  }

}