package org.overviewproject.persistence.orm.finders

import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.persistence.orm.DocumentSetCreationJobTree
import org.overviewproject.persistence.orm.Schema.documentSetCreationJobTrees
import org.overviewproject.tree.orm.finders.{ Finder, FinderResult }

object DocumentSetCreationJobTreeFinder extends Finder {

  type DocumentSetCreationJobTreeFinderResult = FinderResult[DocumentSetCreationJobTree]

  def byJob(jobId: Long): DocumentSetCreationJobTreeFinderResult =
    from(documentSetCreationJobTrees)(jt =>
      where(jt.documentSetCreationJobId === jobId)
        select (jt))

  def byTree(treeId: Long): DocumentSetCreationJobTreeFinderResult =
    from(documentSetCreationJobTrees)(jt =>
      where(jt.treeId === treeId)
        select (jt))
}