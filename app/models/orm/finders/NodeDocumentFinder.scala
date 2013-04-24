package models.orm.finders

import org.overviewproject.postgres.SquerylEntrypoint._
import models.orm.{ NodeDocument, Schema }

object NodeDocumentFinder extends Finder {
  def byDocumentSet(documentSet: Long) : FinderResult[NodeDocument] = {
    // join through documents, not nodes: there are fewer documents than nodes
    // Select as WHERE with a subquery, to circumvent Squeryl delete() missing the join
    val documentIds = from(Schema.documents)(d =>
      where(d.documentSetId === documentSet)
      select(d.id)
    )

    from(Schema.nodeDocuments)(nd =>
      where(nd.documentId in documentIds)
      select(nd)
    )
  }
}
