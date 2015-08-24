package models.orm.finders

import org.squeryl.dsl.GroupWithMeasures
import scala.language.implicitConversions
import scala.language.postfixOps

import com.overviewdocs.postgres.SquerylEntrypoint._
import com.overviewdocs.tree.orm.Tag
import com.overviewdocs.tree.orm.finders.{ Finder, FinderResult }

import org.squeryl.Query

import models.orm.Schema

object TagFinder extends Finder {
  class TagResult(query: Query[Tag]) extends FinderResult(query) {
    def withCounts : FinderResult[(Tag,Long)] = {
      join(toQuery, tagCounts.toQuery.leftOuter)((tags, counts) =>
        select((tags, &(nvl(counts.map(_.measures), 0L))))
        on(tags.id === counts.map(_.key))
      )
    }
  }
  object TagResult {
    implicit def fromQuery(query: Query[Tag]) = new TagResult(query)
  }

  /** @return A mapping from Tag ID to document count */
  def tagCounts : FinderResult[GroupWithMeasures[Long,Long]] = {
    from(Schema.documentTags)(dt =>
      groupBy(dt.tagId)
      compute(count)
    )
  }

  /** @return All `Tag`s with the given DocumentSet. */
  def byDocumentSet(documentSet: Long) : TagResult = {
    from(Schema.tags)(t =>
      where(t.documentSetId === documentSet)
      select(t)
      orderBy(t.name)
    )
  }

  /** @return All `Tag`s with the given DocumentSet and ID. */
  def byDocumentSetAndId(documentSet: Long, id: Long) : TagResult = {
    Schema.tags.where((t) => t.documentSetId === documentSet and t.id === id)
  }
}
