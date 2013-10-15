package org.overviewproject.database.orm.finders

import org.overviewproject.database.orm.Schema
import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.finders.{ Finder, FinderResult }
import org.overviewproject.tree.orm.DocumentSetUser


object DocumentSetUserFinder extends Finder {
	type DocumentSetUserFinderResult = FinderResult[DocumentSetUser]
  
	def byDocumentSet(documentSet: Long): DocumentSetUserFinderResult =
	  Schema.documentSetUsers.where(dsu => dsu.documentSetId === documentSet)
}