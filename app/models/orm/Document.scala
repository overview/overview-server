package models.orm

import org.squeryl.KeyedEntity
import org.squeryl.annotations.Column
import org.overviewproject.postgres.PostgresqlEnum

class DocumentType(v: String) extends PostgresqlEnum(v, "document_type")

case class Document(
    @Column("type") val documentType: DocumentType,
    override val id: Long = 0L,
    @Column("document_set_id") val documentSetId: Long = 0L,
    val title: String = "",
    @Column("supplied_id") val suppliedId: Option[String] = None,
    val text: Option[String] = None,
    val url: Option[String] = None,
    @Column("documentcloud_id") val documentcloudId: Option[String] = None
    ) extends KeyedEntity[Long] {

  // https://www.assembla.com/spaces/squeryl/tickets/68-add-support-for-full-updates-on-immutable-case-classes#/followers/ticket:68
  override def isPersisted(): Boolean = (id > 0)
}

