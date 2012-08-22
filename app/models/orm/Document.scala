package models.orm

import anorm.SQL
import org.squeryl.KeyedEntity
import org.squeryl.PrimitiveTypeMode._
import org.squeryl.annotations.Column

class Document(
    @Column("document_set_id")
    val documentSetId: Long,
    val title: String,
    @Column("text_url")
    val textUrl: String,
    @Column("view_url")
    val viewUrl: String
    ) extends KeyedEntity[Long] {
  override val id: Long = 0

  lazy val documentSet = Schema.documentSetDocuments.right(this)
}
