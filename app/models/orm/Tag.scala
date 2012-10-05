package models.orm

import org.squeryl.annotations.Column
import org.squeryl.KeyedEntity
import org.squeryl.PrimitiveTypeMode._

case class Tag(
  val id: Long = 0l,
  @Column("document_set_id") val documentSetId: Long,
  val name: String,
  val color: Option[String] = None) extends KeyedEntity[Long] {

  def save: Tag = Schema.tags.insert(this)
}
 
object Tag {

  def findByName(documentSetId: Long, name: String): Option[Tag] = {
    Schema.tags.where(t => t.documentSetId === documentSetId and t.name === name).headOption
  }
}
