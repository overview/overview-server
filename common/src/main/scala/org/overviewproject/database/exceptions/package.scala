package org.overviewproject.database

package object exceptions {
  /** You tried to duplicate a primary key or unique index tuple. */
  class Conflict(t: Throwable) extends Exception(t)

  /** You referenced a missing parent object through a foreign key, or you are
    * trying to delete a parent object when children exist. */
  class ParentMissing(t: Throwable) extends Exception(t)
}
