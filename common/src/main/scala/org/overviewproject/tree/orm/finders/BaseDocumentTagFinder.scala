package org.overviewproject.tree.orm.finders

import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.{ DocumentTag, Tag }
import org.squeryl.{ Query, Table }

class BaseDocumentTagFinder(table: Table[DocumentTag], tagsTable: Table[Tag]) extends DocumentSetRelationFinder(table, tagsTable) {

  def byDocumentSetQuery(documentSetId: Long): Query[DocumentTag] =
    relationByDocumentSetComponent(t => t.documentSetId === documentSetId,
        t => t.id, dt => dt.tagId)
    
}