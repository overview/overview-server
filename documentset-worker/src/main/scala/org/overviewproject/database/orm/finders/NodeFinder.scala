package org.overviewproject.database.orm.finders

import org.overviewproject.database.orm.Schema
import org.overviewproject.tree.orm.finders.{ Finder, FinderResult }
import org.overviewproject.tree.orm.Node

object NodeFinder extends Finder {
  type NodeFinderResult = FinderResult[Node]

  def byRootIds(rootIds: Iterable[Long]): NodeFinderResult = {
    import org.overviewproject.postgres.SquerylEntrypoint._
    Schema.nodes.where(d => d.rootId in rootIds)
  }
}
