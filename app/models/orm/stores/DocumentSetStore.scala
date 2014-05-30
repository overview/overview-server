package models.orm.stores

import org.squeryl.{ KeyedEntityDef, Query }

import org.overviewproject.tree.orm._
import org.overviewproject.tree.orm.stores.BaseStore
import org.overviewproject.tree.orm.finders.FinderResult

import models.orm.finders._
import models.orm.stores._
import models.orm.Schema

object DocumentSetStore extends BaseStore(models.orm.Schema.documentSets) {
  override def delete(query: Query[DocumentSet]): Int = {
    throw new AssertionError("Use DocumentSet.deleteOrCancelJob(), not delete()")
  }

  override def delete[K](k: K)(implicit ked: KeyedEntityDef[DocumentSet, K]): Unit = {
    throw new AssertionError("Use DocumentSet.deleteOrCancelJob(), not delete()")
  }
  
  /**
   * Set the deleted flag to true
   */
  def markDeleted(documentSet: DocumentSet): Unit = {
    Schema.documentSets.insertOrUpdate(documentSet.copy(deleted = true))
  }

}
