package models.orm

import org.squeryl.annotations.Column
import org.squeryl.KeyedEntity
import org.squeryl.PrimitiveTypeMode._

case class Tag(
  val id: Long = 0l,
  @Column("document_set_id") val documentSetId: Long,
  val name: String,
  val color: Option[String] = None) extends KeyedEntity[Long] {

  def save: Tag = {
    if (id == 0l) {
      Schema.tags.insert(this)
    }
    else {
      Schema.tags.update(this)
    }
    this
  }

  def delete {
    Schema.tags.deleteWhere(t => t.id === id)
  }
  
  def withUpdate(newName: String, color: String): Tag = copy(name = newName, color = Some(color))
}
 
object Tag {

  def findByName(documentSetId: Long, name: String): Option[Tag] = {
    Schema.tags.where(t => t.documentSetId === documentSetId and t.name === name).headOption
  }
}
