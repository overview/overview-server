package org.overviewproject.persistence.orm.finders

import org.overviewproject.persistence.orm.Schema
import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.FileGroup
import org.overviewproject.tree.orm.finders.{ Finder, FinderResult }


object FileGroupFinder extends Finder {
	type FileGroupFinderResult = FinderResult[FileGroup]
	
	def byId(id: Long): FileGroupFinderResult =
	  Schema.fileGroups.where(fg => fg.id === id)
	  
	  
	
}