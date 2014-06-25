package org.overviewproject.persistence.orm.stores

import org.overviewproject.persistence.orm.Schema.documentSets
import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.stores.BaseStore

object DocumentSetStore extends BaseStore(documentSets) {

  def updateErrorCount(documentSetId: Long, increment: Int) = 
    update(documentSets)(ds =>
      where (ds.id === documentSetId)
      set (ds.documentProcessingErrorCount := ds.documentProcessingErrorCount.~ + increment)
    )
    
}