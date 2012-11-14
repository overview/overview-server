package models.orm

import org.squeryl.KeyedEntity
import org.squeryl.PrimitiveTypeMode._
import org.squeryl.annotations.Column
import org.squeryl.customtypes.StringField

class DocumentType(v: String) extends StringField(v)

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

  def this() = this(documentType = new DocumentType("DocumentCloudDocument")) // For Squeryl

  lazy val documentSet = Schema.documentSetDocuments.right(this)

  // https://www.assembla.com/spaces/squeryl/tickets/68-add-support-for-full-updates-on-immutable-case-classes#/followers/ticket:68
  override def isPersisted(): Boolean = (id > 0)

  def save: Document = Schema.documents.insertOrUpdate(this)

  def delete = Schema.documents.delete(id)
}

object Document {
  def all() = from(Schema.documents)(d => select(d).orderBy(d.id.desc))

  def findById(id: Long) = Schema.documents.lookup(id)
}
