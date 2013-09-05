package org.overviewproject.database.orm

import org.overviewproject.postgres.SquerylEntrypoint.compositeKey
import org.squeryl.KeyedEntity
import org.squeryl.dsl.CompositeKey2

case class FileText(
    fileId: Long,
    text: String) extends KeyedEntity[CompositeKey2[Long, String]] {
  
    override def id = compositeKey(fileId, text)
}