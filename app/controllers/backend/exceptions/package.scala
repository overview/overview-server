package controllers.backend

import java.sql.SQLException

package object exceptions {
  /** You tried to duplicate a primary key or unique index tuple. */
  class Conflict(t: Throwable) extends Exception(t)

  /** You referenced a missing object through a foreign key. */
  class ParentMissing(t: Throwable) extends Exception(t)

  def wrap[T](fn: => T) = try {
    fn
  } catch {
    case e: SQLException => e.getSQLState() match {
      case "23505" => throw new Conflict(e)
      case "23503" => throw new ParentMissing(e)
      case _ => throw e
    }
  }
}
