package models.orm

import org.squeryl.annotations.Column
import org.squeryl.dsl.CompositeKey2
import org.squeryl.KeyedEntity
import org.squeryl.PrimitiveTypeMode.compositeKey

case class DocumentTag(
  @Column("document_id") documentId: Long,
  @Column("tag_id") tagId: Long) extends KeyedEntity[CompositeKey2[Long, Long]] {
  override def id = compositeKey(documentId, tagId)
}
