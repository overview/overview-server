package org.overviewproject.database.orm.stores

import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.stores.BaseStore
import org.overviewproject.database.orm.Schema.{ documents, files }
import org.squeryl.Query
import org.overviewproject.tree.orm.File

object FileStore extends BaseStore(files) {

  def deleteLargeObjectsByDocumentSet(documentSetId: Long): Unit =
    from(files, documents)((f, d) =>
      where(d.documentSetId === documentSetId and d.fileId === f.id)
        select (&(lo_unlink(Some(f.contentsOid))))).toIterable

}
