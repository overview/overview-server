package controllers.backend

import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future

import java.sql.SQLException
import org.apache.lucene.queryparser.classic.ParseException
import org.elasticsearch.action.search.{SearchPhaseExecutionException,ShardSearchFailure}
import org.elasticsearch.{ElasticsearchException,ElasticsearchParseException}

package object exceptions {
  /** You tried to duplicate a primary key or unique index tuple. */
  class Conflict(t: Throwable) extends Exception(t)

  /** You referenced a missing parent object through a foreign key, or you are
    * trying to delete a parent object when children exist. */
  class ParentMissing(t: Throwable) extends Exception(t)

  private def maybeWrapThrowable(t: Throwable): Throwable = t match {
    case e: SQLException => e.getSQLState() match {
      case "23505" => new Conflict(e)
      case "23503" => new ParentMissing(e)
      case _ => System.out.println("OTHER"); e
    }
    case _ => t
  }

  /** Re-casts Future exceptions to Conflict or ParentMissing when appropriate.
    */
  def wrap[T](fn: => Future[T]): Future[T] = fn.transform(identity, maybeWrapThrowable)

  /** Runs the given code, possibly re-throwing exceptions as Conflict or
    * ParentMissing. */
  def wrap[T](fn: => T) = try {
    fn
  } catch {
    case t: Throwable => throw maybeWrapThrowable(t)
  }
}
