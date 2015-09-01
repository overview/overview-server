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
  class TagResult(query: Query[Tag]) extends FinderResult(query)
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

  /** @return All `Tag`s with the given DocumentSet and ID. */
  def byDocumentSetAndId(documentSet: Long, id: Long) : TagResult = {
    Schema.tags.where((t) => t.documentSetId === documentSet and t.id === id)
  }
}
