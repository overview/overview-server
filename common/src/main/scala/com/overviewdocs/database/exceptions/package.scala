package com.overviewdocs.database

import java.sql.SQLException

package object exceptions {
  /** You tried to duplicate a primary key or unique index tuple. */
  class Conflict(ex: SQLException) extends Exception(ex)

  /** You referenced a missing parent object through a foreign key, or you are
    * trying to delete a parent object when children exist. */
  class ParentMissing(ex: SQLException) extends Exception(ex)

  /** You used a Large Object ID that does not point to a valid Large Object. */
  class UndefinedObject(ex: SQLException) extends Exception(ex)
}
