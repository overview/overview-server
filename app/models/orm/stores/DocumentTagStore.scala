package models.orm.stores

import org.squeryl.Query

import models.orm.Schema
import org.overviewproject.tree.orm.Document
import org.overviewproject.tree.orm.stores.BaseStore

object DocumentTagStore extends BaseStore(Schema.documentTags) {
  def insertForTagAndDocuments(tagId: Long, documents: Query[Document]) : Int = {
    import org.overviewproject.postgres.SquerylEntrypoint._

    val query: Query[(Long,Long)] = from(documents)(d =>
      where(d.id notIn from(Schema.documentTags)(dt => where(dt.tagId === tagId) select(dt.documentId)))
      select((d.id, &(longTEF.createConstant(tagId))))
    )

    insertSelect(query)
  }
}
