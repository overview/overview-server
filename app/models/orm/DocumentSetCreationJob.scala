package models.orm

import org.squeryl.annotations.Column
import org.squeryl.KeyedEntity
import org.squeryl.dsl.ManyToOne

class DocumentSetCreationJob(
    val query: String, 
    @Column("user_id")
    val userId: Long,
    val state: Int = 0
   ) extends KeyedEntity[Long] {
  override val id: Long = 0

  lazy val user: ManyToOne[User] = Schema.userToDocumentSetCreationJobs.right(this)
}