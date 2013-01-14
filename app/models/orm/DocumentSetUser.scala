package models.orm

import org.squeryl.KeyedEntity
import org.overviewproject.postgres.SquerylEntrypoint.compositeKey
import org.squeryl.dsl.CompositeKey2
import org.squeryl.annotations.Column

class DocumentSetUser(
    @Column("document_set_id")
    val documentSetId: Long,
    @Column("user_id")
    val userId: Long
    ) extends KeyedEntity[CompositeKey2[Long,Long]] {
  override def id = compositeKey(documentSetId, userId)
}
