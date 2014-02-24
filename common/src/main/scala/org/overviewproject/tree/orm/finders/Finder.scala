package org.overviewproject.tree.orm.finders

import scala.language.implicitConversions

import org.squeryl.Query
import org.squeryl.dsl.GroupWithMeasures

trait Finder {
  implicit protected def queryGroupWithMeasuresToFinderResult[A,B](query: Query[GroupWithMeasures[A,B]]) : FinderResult[(A,B)] = {
    import org.overviewproject.postgres.SquerylEntrypoint._
    val wrapper = from(query)(q => select((q.key, q.measures)))
    new FinderResult(wrapper)
  }

  implicit protected def queryToFinderResult[A](query: Query[A]) = new FinderResult(query)
}
