package models.orm.finders

import models.orm.Schema
import org.squeryl.Table
import scala.language.postfixOps

import com.overviewdocs.postgres.SquerylEntrypoint._
import com.overviewdocs.tree.orm.finders.{DocumentSetComponentFinder,FinderResult}
import com.overviewdocs.tree.orm.Tree

object TreeFinder extends DocumentSetComponentFinder[Tree] {
  override protected val table = Schema.trees

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
