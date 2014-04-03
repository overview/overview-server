package org.overviewproject.tree.orm

import org.overviewproject.postgres.SquerylEntrypoint._
import org.squeryl.KeyedEntity

/**
 * Keep track of shared PDF uploads stored in the large object with `contentsOid`.
 * As documents get copied and deleted, `referenceCount` should be updated. When 
 * `referenceCount` is 0, the `File` and associated large object can be deleted.
 */
case class File(
    referenceCount: Int, 
    contentsOid: Long, 
    name: String,
    id: Long = 0l) extends KeyedEntity[Long] {
  override def isPersisted(): Boolean = (id > 0)
}