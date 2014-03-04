package org.overviewproject.persistence.orm.finders

import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.persistence.orm.Schema.documentSetCreationJobTrees
import org.overviewproject.tree.orm.DocumentSetCreationJobTree
import org.overviewproject.tree.orm.finders.{ BaseDocumentSetCreationJobTreeFinder, FinderResult }

object DocumentSetCreationJobTreeFinder extends BaseDocumentSetCreationJobTreeFinder(documentSetCreationJobTrees) {

  type DocumentSetCreationJobTreeFinderResult = FinderResult[DocumentSetCreationJobTree]

  def byJob(jobId: Long): DocumentSetCreationJobTreeFinderResult =
   byJobQuery(jobId)
   
  def byTree(treeId: Long): DocumentSetCreationJobTreeFinderResult =
    from(documentSetCreationJobTrees)(jt =>
      where(jt.treeId === treeId)
        select (jt))
}