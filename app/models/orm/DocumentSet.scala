package models.orm

import org.squeryl.KeyedEntity

class DocumentSet(val query: String) extends KeyedEntity[Long] {
  override val id: Long = 0

  lazy val users = Schema.documentSetUsers.left(this)
}
