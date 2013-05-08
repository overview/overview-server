package models.orm.finders

import org.squeryl.Query
import org.squeryl.dsl.GroupWithMeasures
import scala.language.implicitConversions
import scala.language.postfixOps

import org.overviewproject.postgres.SquerylEntrypoint._
import models.orm.{ Schema, Tag }

object TagFinder extends Finder {
  class TagResult(query: Query[Tag]) extends FinderResult(query) {
    def withCounts : FinderResult[(Tag,Long)] = {
      val tagCounts : Query[GroupWithMeasures[Long,Long]] = from(Schema.documentTags)(t =>
        groupBy(t.tagId)
        compute(org.overviewproject.postgres.SquerylEntrypoint.count) // NOT FinderResult.count
      )

      join(toQuery, tagCounts.leftOuter)((tags, counts) =>
        select(tags, &(nvl(counts.map(_.measures), 0L)))
        on(tags.id === counts.map(_.key))
      )
    }
  }
  object TagResult {
    implicit def fromQuery(query: Query[Tag]) = new TagResult(query)
  }

  /** @return All `Tag`s with the given DocumentSet. */
  def byDocumentSet(documentSet: Long) : TagResult = {
    from(Schema.tags)(t =>
      where(t.documentSetId === documentSet)
      select(t)
      orderBy(t.name)
    )
  }
}
