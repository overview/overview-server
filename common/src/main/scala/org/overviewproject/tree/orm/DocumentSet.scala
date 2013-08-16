package org.overviewproject.tree.orm

import java.sql.Timestamp
import org.squeryl.KeyedEntity
import org.squeryl.annotations.Column
import scala.language.implicitConversions
import org.overviewproject.util.DocumentSetVersion

case class DocumentSet(
  override val id: Long = 0,
  title: String = "",
  query: Option[String] = None,
  lang: String = "en",
  @Column("public") isPublic: Boolean = false,
  suppliedStopWords: String = "",
  createdAt: Timestamp = new Timestamp(scala.compat.Platform.currentTime),
  documentCount: Int = 0,
  documentProcessingErrorCount: Int = 0,
  importOverflowCount: Int = 0,
  uploadedFileId: Option[Long] = None,
  version: Int = DocumentSetVersion.current) extends KeyedEntity[Long] {

  // https://www.assembla.com/spaces/squeryl/tickets/68-add-support-for-full-updates-on-immutable-case-classes#/followers/ticket:68
  override def isPersisted(): Boolean = (id > 0)
}

object DocumentSet {
  implicit def toLong(documentSet: DocumentSet) = documentSet.id
}
