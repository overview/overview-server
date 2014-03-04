package org.overviewproject.tree.orm.finders

import org.overviewproject.postgres.SquerylEntrypoint._
import org.squeryl.{ Table, Query }
import org.overviewproject.tree.orm.DocumentSetCreationJobTree


class BaseDocumentSetCreationJobTreeFinder(table: Table[DocumentSetCreationJobTree]) extends Finder {

  protected def byJobQuery(jobId: Long): Query[DocumentSetCreationJobTree] = 
    from(table) { dscjt => 
      where (dscjt.documentSetCreationJobId === jobId)
      select (dscjt)
    }
}