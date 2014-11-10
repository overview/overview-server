package controllers.backend

import java.sql.SQLException
import org.apache.lucene.queryparser.classic.ParseException
import org.elasticsearch.action.search.{SearchPhaseExecutionException,ShardSearchFailure}
import org.elasticsearch.ElasticSearchException

package object exceptions {
  /** You tried to duplicate a primary key or unique index tuple. */
  class Conflict(t: Throwable) extends Exception(t)

  /** You referenced a missing parent object through a foreign key, or you are
    * trying to delete a parent object when children exist. */
  class ParentMissing(t: Throwable) extends Exception(t)

  /** You submitted an invalid search string.
    *
    * User input can trigger this exception, because we let users enter
    * arbitrary queries.
    *
    * The exception comes from a remote server with a nested ParseException.
    * The nesting can be arbitrarily deep.
    */
  class SearchParseFailed(message: String, t: Throwable) extends Exception(message, t)

  /** Runs the given code, possibly re-throwing exceptions as Conflict or
    * ParentMissing. */
  def wrap[T](fn: => T) = try {
    fn
  } catch {
    case e: SQLException => e.getSQLState() match {
      case "23505" => throw new Conflict(e)
      case "23503" => throw new ParentMissing(e)
      case _ => throw e
    }
  }

  /** Runs the given code, possibly re-throwing exceptions as
    * SearchParseFailed. */
  def wrapElasticSearchException(t: Throwable): Throwable = {
    /*
     * In ElasticSearch, we need to sort through:
     *
     * [overview-server] Caused by: org.elasticsearch.transport.RemoteTransportException: [Search Index][inet[/127.0.0.1:9300]][search]
     * [overview-server] Caused by: org.elasticsearch.action.search.SearchPhaseExecutionException: Failed to execute phase [query_fetch],
     * total failure; shardFailures {[bEWy9lL4QuS_EqbmNUJb0A][documents_v1][0]: SearchParseException[[documents_v1][0]:
     * from[-1],size[10000000]: Parse Failure [Failed to parse source
     * [{"size":10000000,"query":{"bool":{"must":[{"term":{"document_set_id":763}},{"query_string":{"query":"bar["}}]}},"fields":"id"}]]];
     * nested: QueryParsingException[[documents_v1] Failed to parse query [bar[]]; nested: ParseException[Cannot parse 'bar[': Encountered
     * "<EOF>" at line 1, column 4.
     * [overview-server] Was expecting one of:
     * [overview-server]     <RANGE_QUOTED> ...
     * [overview-server]     <RANGE_GOOP> ...
     * [overview-server]     ]; nested: ParseException[Encountered "<EOF>" at line 1, column 4.
     * [overview-server] Was expecting one of:
     * [overview-server]     <RANGE_QUOTED> ...
     * [overview-server]     <RANGE_GOOP> ...
     * [overview-server]     ]; }
     * [overview-server]  at
     * org.elasticsearch.action.search.type.TransportSearchTypeAction$BaseAsyncAction.onFirstPhaseResult(TransportSearchTypeAction.java:261)
     * ~[elasticsearch-0.90.2.jar:na]
     */
    def findParseError(t: Any): Option[String] = t match {
      case s: String => Some(s.split("; nested: \\w+\\[").takeRight(2).head.split("\n").head) // because there's no ShardSearchFailure.failure() in v0.90.2
      case ssf: ShardSearchFailure => findParseError(ssf.reason) // should recurse through ssf.failure() but that doesn't exist in v0.90.2 :(
      case spee: SearchPhaseExecutionException => spee.shardFailures.flatMap(findParseError(_)).headOption
      case ese: ElasticSearchException if (ese.getCause != null) => findParseError(ese.getCause)
      case _ => None
    }

    findParseError(t) match {
      case Some(s) => new SearchParseFailed(s, t)
      case None => t
    }
  }
}
