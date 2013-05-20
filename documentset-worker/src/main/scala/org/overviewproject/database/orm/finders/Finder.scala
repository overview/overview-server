package org.overviewproject.database.orm.finders

import org.squeryl.Query
import scala.language.implicitConversions

trait Finder {
  implicit protected def queryToFinderResult[A](query: Query[A]) = new FinderResult(query)
}
