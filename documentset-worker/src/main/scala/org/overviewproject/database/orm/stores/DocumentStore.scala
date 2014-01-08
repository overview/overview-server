package org.overviewproject.database.orm.stores

import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.database.orm.Schema.documents
import org.overviewproject.tree.orm.stores.{ BaseStore, NoInsertOrUpdate }
import org.overviewproject.tree.orm.Document
import org.squeryl.Query


object DocumentStore extends BaseStore(documents) with NoInsertOrUpdate[Document] {
  
  def deleteContents(query: Query[Document]) = {
    from(query)(d =>
      select(&(lo_unlink(d.contentsOid)))  
    ).toIterable
  }

}