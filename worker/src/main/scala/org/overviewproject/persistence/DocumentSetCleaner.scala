/*
 * DocumentSetCleaner.scala
 *
 * Overview Project
 * Created by Jonas Karlsson, Oct 2012
 */

package org.overviewproject.persistence

import org.overviewproject.persistence.orm.stores.NodeDocumentStore
import org.overviewproject.tree.orm.stores.BaseStore
import org.overviewproject.persistence.orm.Schema.{ documents, documentSetCreationJobTrees, nodes, trees }
import org.overviewproject.tree.orm.finders.DocumentSetComponentFinder
import org.squeryl.Table
import org.overviewproject.tree.orm.DocumentSetComponent
import org.overviewproject.tree.orm.stores.BaseNodeStore
import org.overviewproject.persistence.orm.finders.DocumentSetCreationJobTreeFinder
import org.overviewproject.tree.orm.finders.{ Finder, FinderResult }
import org.overviewproject.persistence.orm.finders.NodeFinder
import org.overviewproject.persistence.orm.finders.TreeFinder

/**
 * Deletes all data associated with a document set in the database
 * but leaves the document set itself.
 */
class DocumentSetCleaner {

  /** remove node and document data associated with specified documentSetId */
  def clean(jobId: Long, documentSetId: Long) {
    val tree = findTreeId(jobId)
    tree.foreach(removeNodeData)
    
    removeDocumentData(documentSetId)
  }

  private def findTreeId(jobId: Long): Option[Long] = 
    DocumentSetCreationJobTreeFinder.byJob(jobId).headOption.map(_.treeId)
    
  private def removeNodeData(treeId: Long): Unit = {
    val nodeStore = BaseNodeStore(nodes, trees)
    NodeDocumentStore.deleteByTree(treeId)
    deleteByQuery(nodes, NodeFinder.byTree(treeId))
    deleteByQuery(documentSetCreationJobTrees, DocumentSetCreationJobTreeFinder.byTree(treeId))
    deleteByQuery(trees, TreeFinder.byId(treeId))
  }

  private def removeDocumentData(documentSetId: Long): Unit = {
    deleteByDocumentSetId(documents, documentSetId)
  }

  private def deleteByQuery[A](table: Table[A], queryResult: FinderResult[A]): Int =
    table.delete(queryResult.toQuery)
    
  private def deleteByDocumentSetId[A <: DocumentSetComponent](table: Table[A], documentSetId: Long): Int =
    BaseStore(table).delete(DocumentSetComponentFinder(table).byDocumentSet(documentSetId))
}
