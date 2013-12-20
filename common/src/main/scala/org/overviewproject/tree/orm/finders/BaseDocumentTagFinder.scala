package org.overviewproject.tree.orm.finders

import org.overviewproject.postgres.SquerylEntrypoint._
import org.squeryl.Table
import org.overviewproject.tree.orm.{ DocumentTag, Tag }
import org.squeryl.Query

class BaseDocumentTagFinder(table: Table[DocumentTag], tagsTable: Table[Tag]) extends Finder {

  def byDocumentSetQuery(documentSetId: Long): Query[DocumentTag] = {
    // Join through tags should be faster: there are usually fewer tags than documents
    // Select as WHERE with a subquery, to circumvent Squeryl delete() missing the join
    val tagIds = from(tagsTable)(t =>
      where(t.documentSetId === documentSetId)
        select (t.id))

    table.where(_.tagId in tagIds)

  }
}