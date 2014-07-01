/*
 * DocumentSetCleaner.scala
 *
 * Overview Project
 * Created by Jonas Karlsson, Oct 2012
 */

package org.overviewproject.persistence

import org.squeryl.Table

import org.overviewproject.persistence.orm.stores.NodeDocumentStore
import org.overviewproject.tree.orm.Tree
import org.overviewproject.tree.orm.stores.BaseStore
import org.overviewproject.persistence.orm.Schema.{ documents, documentSetCreationJobNodes, nodes, trees }
import org.overviewproject.tree.orm.finders.DocumentSetComponentFinder
import org.overviewproject.tree.orm.DocumentSetComponent
import org.overviewproject.tree.orm.finders.{ Finder, FinderResult }
import org.overviewproject.persistence.orm.finders.{NodeFinder,TreeFinder}

/**
 * Deletes all data associated with a document set in the database
 * but leaves the document set itself.
 */
class DocumentSetCleaner {
  /** remove node and document data associated with specified documentSetId */
  def clean(jobId: Long, documentSetId: Long) {
    removeTreeData(jobId)
    removeTreelessNodeData(jobId)

    if (noRemainingTrees(documentSetId))
      removeDocumentData(documentSetId)
  }

  private def findTree(jobId: Long): Option[Tree] =
    TreeFinder.byJobId(jobId).headOption

  private def noRemainingTrees(documentSetId: Long): Boolean =
    TreeFinder.byDocumentSet(documentSetId).count == 0

  private def removeTreeData(jobId: Long): Unit = {
    /*
     * This is a bit silly. If the tree has been written to the database, we
     * know the clustering completed and so the job completed, so this code
     * path ought to be dead.
     *
     * See https://www.pivotaltracker.com/story/show/74178600
     */
    for (tree <- TreeFinder.byJobId(jobId).headOption) {
      deleteByQuery(trees, TreeFinder.byJobId(jobId))
      NodeDocumentStore.deleteByRoot(tree.rootNodeId)
      deleteByQuery(nodes, NodeFinder.byRoot(tree.rootNodeId))
    }
  }

  private def removeTreelessNodeData(jobId: Long): Unit = {
    import org.overviewproject.postgres.SquerylEntrypoint._

    for (jobAndNode <- documentSetCreationJobNodes.where(_.documentSetCreationJobId === jobId).headOption) {
      val rootId = jobAndNode.nodeId
      NodeDocumentStore.deleteByRoot(rootId)
      deleteByQuery(nodes, NodeFinder.byRoot(rootId))
    }

    documentSetCreationJobNodes.deleteWhere(_.documentSetCreationJobId === jobId)
  }

  private def removeDocumentData(documentSetId: Long): Unit = {
    deleteByDocumentSetId(documents, documentSetId)
  }

  private def deleteByQuery[A](table: Table[A], queryResult: FinderResult[A]): Int =
    table.delete(queryResult.toQuery)

  private def deleteByDocumentSetId[A <: DocumentSetComponent](table: Table[A], documentSetId: Long): Int =
    BaseStore(table).delete(DocumentSetComponentFinder(table).byDocumentSet(documentSetId))
}
