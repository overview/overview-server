package models.orm.finders

import models.orm.Schema.trees
import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.finders.{ DocumentSetComponentFinder, FinderResult }
import org.overviewproject.tree.orm.Tree
import org.squeryl.Table

object TreeFinder extends DocumentSetComponentFinder[Tree] {
  type TreeFinderResult = FinderResult[Tree]
  
  override protected val table: Table[Tree] = trees
  
  def byId(id: Long): TreeFinderResult = 
    table.where(_.id === id)
}