package models.orm

import org.squeryl.annotations.Column
import org.squeryl.KeyedEntity
import org.squeryl.dsl.ManyToOne

class DocumentSetCreationJob(
    @Column("document_set_id")
    val documentSetId: Long,
    val state: Int = 0,
    val fraction_complete: Double = 0.0,
    val status_description: String = ""
   ) extends KeyedEntity[Long] {
  override val id: Long = 0

  lazy val documentSet: ManyToOne[DocumentSet] = 
    Schema.documentSetDocumentSetCreationJobs.right(this);
}
