package com.overviewdocs.tree.orm.finders

import com.overviewdocs.tree.orm.DocumentSetComponent
import org.squeryl.Table
import com.overviewdocs.postgres.SquerylEntrypoint._

trait DocumentSetComponentFinder[A <: DocumentSetComponent] extends Finder {
  protected val table: Table[A]

  def byDocumentSet(documentSetId: Long): FinderResult[A] =
    from(table)(c =>
      where(c.documentSetId === documentSetId)
        select (c))

}

object DocumentSetComponentFinder {
  def apply[A <: DocumentSetComponent](componentTable: Table[A]) = new DocumentSetComponentFinder[A] {
	override val table: Table[A] = componentTable
  }

}