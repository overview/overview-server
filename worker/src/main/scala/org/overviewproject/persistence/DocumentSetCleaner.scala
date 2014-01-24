/*
 * DocumentSetCleaner.scala
 *
 * Overview Project
 * Created by Jonas Karlsson, Oct 2012
 */

package org.overviewproject.persistence

import org.overviewproject.persistence.orm.stores.NodeDocumentStore
import org.overviewproject.tree.orm.stores.BaseStore
import org.overviewproject.persistence.orm.Schema.{ documents, nodes, trees }
import org.overviewproject.tree.orm.finders.DocumentSetComponentFinder
import org.squeryl.Table
import org.overviewproject.tree.orm.DocumentSetComponent

/**
 * Deletes all data associated with a document set in the database
 * but leaves the document set itself.
 */
class DocumentSetCleaner {

  /** remove node and document data associated with specified documentSetId */
  def clean(documentSetId: Long) {
    removeNodeData(documentSetId)
    removeDocumentData(documentSetId)
  }

  private def removeNodeData(documentSetId: Long): Unit = {
    NodeDocumentStore.deleteByDocumentSetId(documentSetId)
    deleteByDocumentSetId(nodes, documentSetId)
    deleteByDocumentSetId(trees, documentSetId)
  }

  private def removeDocumentData(documentSetId: Long): Unit = {
    deleteByDocumentSetId(documents, documentSetId)
  }

  private def deleteByDocumentSetId[A <: DocumentSetComponent](table: Table[A], documentSetId: Long): Int =
    BaseStore(table).delete(DocumentSetComponentFinder(table).byDocumentSet(documentSetId))
}
