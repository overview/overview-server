package org.overviewproject.persistence.orm.finders

import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.finders.DocumentSetComponentFinder
import org.overviewproject.tree.orm.Tree
import org.overviewproject.persistence.orm.Schema.trees
import org.overviewproject.tree.orm.finders.FinderResult
import org.squeryl.Table

object TreeFinder extends DocumentSetComponentFinder[Tree] {

  override protected val table: Table[Tree] = trees
  
  type TreeFinderResult = FinderResult[Tree]
  
  def byId(id: Long): TreeFinderResult = 
    from(table)(t =>
      where (t.id === id)
      select (t))
}