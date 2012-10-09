package models.orm

import org.squeryl.KeyedEntity
import org.squeryl.PrimitiveTypeMode._
import org.squeryl.annotations.Column

case class Node (
    val id: Long = 0,
    @Column("document_set_id")
    val documentSetId: Long = 0,
    val description: String = ""
    ) extends KeyedEntity[Long] {

  override def isPersisted(): Boolean = (id > 0)

  def save(): Node = Schema.nodes.insertOrUpdate(this)
}
