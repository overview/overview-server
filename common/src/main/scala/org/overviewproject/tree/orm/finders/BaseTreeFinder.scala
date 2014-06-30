package org.overviewproject.tree.orm.finders

import org.squeryl.Table
import scala.language.postfixOps

import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.Tree

class BaseTreeFinder(override val table: Table[Tree]) extends DocumentSetComponentFinder[Tree] {
  type TreeFinderResult = FinderResult[Tree]

  /** All Trees for the given document sets.
    *
    * These are ordered from newest to oldest.
    */
  def byDocumentSets(documentSets: Iterable[Long]): TreeFinderResult = {
    from(table)(t =>
      where(t.documentSetId in documentSets)
      select(t)
      orderBy(t.createdAt desc)
    )
  }

  /** All Trees for the given document set.
    *
    * These are ordered from newest to oldest.
    */
  override def byDocumentSet(documentSet: Long) : TreeFinderResult = {
    from(table)(t =>
      where(t.documentSetId === documentSet)
      select(t)
      orderBy(t.createdAt desc)
    )
  }
  
  def byId(id: Long): TreeFinderResult = {
    table.where(_.id === id)
  }

  def byJobId(jobId: Long): TreeFinderResult = {
    table.where(_.jobId === jobId)
  }
}
