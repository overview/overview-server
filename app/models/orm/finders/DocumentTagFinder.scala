package models.orm.finders

import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.DocumentTag
import models.orm.Schema

object DocumentTagFinder extends Finder {
  def byDocumentSet(documentSet: Long) : FinderResult[DocumentTag] = {
    // Join through tags should be faster: there are usually fewer tags than documents
    // Select as WHERE with a subquery, to circumvent Squeryl delete() missing the join
    val tagIds = from(Schema.tags)(t =>
      where(t.documentSetId === documentSet)
      select(t.id)
    )

    from(Schema.documentTags)(dt =>
      where(dt.tagId in tagIds)
      select(dt)
    )
  }
}
