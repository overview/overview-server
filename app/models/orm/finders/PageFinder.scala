package models.orm.finders

import models.orm.Schema
import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.finders.{ Finder, FinderResult }
import org.overviewproject.tree.orm.Page

object PageFinder extends Finder {
 type PageResult = FinderResult[Page]
 
 def byId(id: Long): PageResult = Schema.pages.where(_.id === id)
}