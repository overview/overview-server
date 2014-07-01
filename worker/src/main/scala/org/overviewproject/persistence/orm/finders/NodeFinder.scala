package org.overviewproject.persistence.orm.finders

import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.finders.Finder
import org.overviewproject.tree.orm.finders.FinderResult
import org.overviewproject.tree.orm.Node
import org.overviewproject.persistence.orm.Schema.nodes

object NodeFinder extends Finder {
  type NodeFinderResult = FinderResult[Node]

  def byRoot(rootId: Long): NodeFinderResult =
    from(nodes)(n =>
      where (n.rootId === rootId)
      select(n)
    )
}
