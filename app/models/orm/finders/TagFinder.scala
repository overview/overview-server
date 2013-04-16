package models.orm.finders

import org.squeryl.Query
import scala.language.implicitConversions
import scala.language.postfixOps

import org.overviewproject.postgres.SquerylEntrypoint._
import models.orm.{ Schema, Tag }

object TagFinder extends Finder {
  /** @return All `Tag`s with the given DocumentSet. */
  def byDocumentSet(documentSet: Long) : FinderResult[Tag] = {
    from(Schema.tags)(t =>
      where(t.documentSetId === documentSet)
      select(t)
      orderBy(t.name)
    )
  }
}
