package org.overviewproject.tree.orm

import org.squeryl.KeyedEntity
import org.squeryl.annotations.Column
import org.overviewproject.postgres.PostgresqlEnum

class DocumentType(v: String) extends PostgresqlEnum(v, "document_type")

object DocumentType {
  val DocumentCloudDocument = new DocumentType("DocumentCloudDocument")
  val CsvImportDocument = new DocumentType("CsvImportDocument")
}

case class Document(
  @Column("type") val documentType: DocumentType,
  @Column("document_set_id") val documentSetId: Long = 0L,
  val title: Option[String] = Some(""),
  @Column("supplied_id") val suppliedId: Option[String] = None,
  val text: Option[String] = None,
  val url: Option[String] = None,
  @Column("documentcloud_id") val documentcloudId: Option[String] = None,
  override val id: Long = 0L) extends KeyedEntity[Long] {

  // https://www.assembla.com/spaces/squeryl/tickets/68-add-support-for-full-updates-on-immutable-case-classes#/followers/ticket:68
  override def isPersisted(): Boolean = (id > 0)
}

