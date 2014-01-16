package models.orm.finders

import models.orm.Schema
import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.finders.{ Finder, FinderResult }
import org.overviewproject.tree.orm.File

object FileFinder extends Finder {
  type FileResult = FinderResult[File]
  
  def byId(id: Long): FileResult = Schema.files.where(_.id === id)
}