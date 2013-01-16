package org.overviewproject.tree.orm

import org.squeryl.annotations.Column
import org.squeryl.KeyedEntity

case class DocumentProcessingError(
    @Column("document_set_id") documentSetId: Long,
    @Column("text_url") textUrl: String,
    message: String,
    @Column("status_code") statusCode: Option[Int] = None,
    headers: Option[String] = None,
    id: Long = 0l) extends KeyedEntity[Long] {

}