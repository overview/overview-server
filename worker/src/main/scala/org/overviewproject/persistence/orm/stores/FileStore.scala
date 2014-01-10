package org.overviewproject.persistence.orm.stores

import scala.language.postfixOps

import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.stores.BaseStore
import org.overviewproject.tree.orm.GroupedProcessedFile
import org.overviewproject.persistence.orm.Schema
import org.squeryl.Query

object FileStore extends BaseStore(Schema.groupedProcessedFiles) {
  
  /**
   * Delete large objects associated with failed pdf extraction, otherwise
   * we lose all references to the oid.
   */
  override def delete(query: Query[GroupedProcessedFile]): Int = {
    from(query)(g =>
      where (g.text isNull)
      select (&(lo_unlink(Some(g.contentsOid))))).toIterable

    
    super.delete(query)
  }
}