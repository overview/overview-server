package models.orm

import org.squeryl.annotations.Column
import org.squeryl.KeyedEntity
import org.squeryl.dsl.ManyToOne

class DocumentSetCreationJob(
    val query: String, 
    val state: Int = 0
   ) extends KeyedEntity[Long] {
  override val id: Long = 0

  @Column("user_id")
  var userId: Long = _

  lazy val user: ManyToOne[User] = Schema.userToDocumentSetCreationJobs.right(this)
}