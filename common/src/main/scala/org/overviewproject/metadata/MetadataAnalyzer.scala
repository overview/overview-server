package org.overviewproject.metadata

import play.api.libs.iteratee._
import play.api.libs.json.JsObject
import scala.concurrent.ExecutionContext

/** Operations related to metadata that don't fit anywhere else. */
object MetadataAnalyzer {
  /** Transforms JSON objects into a stream of unique String field names.
    *
    * The result is ordered by occurrence. Keys within an individual JSON
    * Object are unordered, of course; but if the first Object contains a key
    * `foo` and no key `bar`, then `foo` will appear in the result before `bar`.
    */
  def uniqueFieldNames(implicit ec: ExecutionContext): Enumeratee[JsObject,String] = {
    fieldNamesWithRepeats.compose(uniq)
  }

  /** Enumerates all field names from all input elements. */
  private def fieldNamesWithRepeats(implicit ec: ExecutionContext): Enumeratee[JsObject,String] = {
    Enumeratee.mapConcat { (jsObject: JsObject) => jsObject.keys.toSeq }
  }

  /** Filter: only passes through elements that have not yet been seen. */
  private def uniq[A](implicit ec: ExecutionContext): Enumeratee[A,A] = new Enumeratee.CheckDone[A,A] {
    // We *could* do this as a two-liner with a mutable.Set[String]. Or we
    // could do things the functional-programming way. I'm not sure what we win
    // this way, but I learned a lot in the process.

    def skip[B](seen: Set[A])(k: K[A,B]) = {
      new Enumeratee.CheckDone[A,A] { def continue[B](k: K[A,B]) = Cont(step(seen)(k)) } &> k(Input.Empty)
    }

    def emit[B](in: Input.El[A])(seen: Set[A])(k: K[A,B]) = {
      new Enumeratee.CheckDone[A,A] { def continue[B](k: K[A,B]) = Cont(step(seen + in.e)(k)) } &> k(in)
    }

    // I used Enumeratee.take as a template for this one
    def step[B](seen: Set[A])(k: K[A,B]): K[A,Iteratee[A,B]] = {
      case Input.Empty => skip(seen)(k)
      case in @ Input.El(_) if seen.contains(in.e) => skip(seen)(k)
      case in @ Input.El(_) => emit(in)(seen)(k)
      case Input.EOF => Done(Cont(k), Input.EOF)
    }

    override def continue[B](k: K[A,B]) = Cont(step(Set.empty[A])(k))
  }
}
