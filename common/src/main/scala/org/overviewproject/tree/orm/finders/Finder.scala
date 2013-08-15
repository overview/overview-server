package org.overviewproject.tree.orm.finders

import scala.language.implicitConversions

import org.squeryl.Query

trait Finder {
  implicit protected def queryToFinderResult[A](query: Query[A]) = new FinderResult(query)
}
